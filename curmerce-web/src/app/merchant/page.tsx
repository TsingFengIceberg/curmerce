"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Boxes, Clock3, ExternalLink, RotateCcw, Truck } from "lucide-react";
import { DashboardHeader, DashboardMetrics, QuickActions } from "@/components/dashboard-ui";
import { Notice } from "@/components/notice";
import { adminOrderApi } from "@/lib/api/admin-order";
import { adminProductApi } from "@/lib/api/admin-product";
import { adminRefundApi } from "@/lib/api/admin-refund";
import { CurmerceApiError } from "@/lib/api/client";
import { ensureMerchantOwner } from "@/lib/auth/guards";
import { formatDateTime, formatMoney, formatOrderStatus } from "@/lib/format";
import type { MerchantOrder } from "@/lib/types/api";

const initial = { shipping: 0, reviewing: 0, selling: 0, refunds: 0 };

export default function MerchantDashboardPage() {
  const router = useRouter();
  const [counts, setCounts] = useState(initial);
  const [recentOrders, setRecentOrders] = useState<MerchantOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void ensureMerchantOwner(router).then((allowed) => {
      if (!allowed) return;
      void Promise.all([
        adminOrderApi.pageOwn({ pageNo: 1, pageSize: 1, status: 20 }),
        adminProductApi.pageOwn({ pageNo: 1, pageSize: 1, auditStatus: 1 }),
        adminProductApi.pageOwn({ pageNo: 1, pageSize: 1, auditStatus: 2, saleStatus: 1 }),
        adminRefundApi.pageOwn({ pageNo: 1, pageSize: 1, status: 10 }),
        adminOrderApi.pageOwn({ pageNo: 1, pageSize: 5 }),
      ]).then(([shipping, reviewing, selling, refunds, orders]) => {
        setCounts({ shipping: shipping?.total ?? 0, reviewing: reviewing?.total ?? 0, selling: selling?.total ?? 0, refunds: refunds?.total ?? 0 });
        setRecentOrders(orders?.list ?? []);
      })
        .catch((cause) => setError(cause instanceof CurmerceApiError ? cause.message : "经营数据加载失败"))
        .finally(() => setLoading(false));
    });
  }, [router]);

  const metrics = [
    { label: "待发货订单", value: counts.shipping, hint: "需要尽快履约", href: "/merchant/orders", icon: Truck },
    { label: "审核中商品", value: counts.reviewing, hint: "等待平台反馈", href: "/merchant/products", icon: Clock3 },
    { label: "在售商品", value: counts.selling, hint: "当前公开销售", href: "/merchant/products", icon: Boxes },
    { label: "待处理退款", value: counts.refunds, hint: "查看买家申请", href: "/merchant/refunds", icon: RotateCcw },
  ];

  return (
    <section className="workspace-dashboard">
      <DashboardHeader eyebrow="STORE OVERVIEW" title="经营概览" description="从今日待办开始，管理商品、履约、活动和售后。" />
      {error ? <Notice>{error}</Notice> : null}
      <DashboardMetrics items={metrics} loading={loading} />
      <div className="dashboard-grid">
        <section className="workspace-section dashboard-recent-orders">
          <div className="workspace-section__heading"><h2>近期订单</h2><Link className="text-button button--icon-label" href="/merchant/orders">全部订单<ExternalLink aria-hidden="true" size={14} /></Link></div>
          {loading ? <div className="dashboard-order-skeleton"><span /><span /><span /></div> : recentOrders.length ? <div className="dashboard-order-list">{recentOrders.map((order) => <Link href="/merchant/orders" key={order.id}><span><strong>{order.orderNo}</strong><small>{order.buyerNickname || "买家"} · {formatDateTime(order.createTime)}</small></span><b>{formatMoney(order.payableAmount)}</b><em className={`tag order-status order-status--${order.status}`}>{formatOrderStatus(order.status)}</em></Link>)}</div> : <p className="dashboard-empty">还没有订单，商品成交后会显示在这里。</p>}
        </section>
        <QuickActions title="开始经营" items={[{ href: "/merchant/products", label: "创建或维护商品", description: "管理商品资料、SKU、库存和上架状态" }, { href: "/merchant/releases", label: "创建限时发售", description: "选择在售 SKU 配置限时活动" }, { href: "/merchant/auctions", label: "创建拍卖", description: "使用有库存的商品发起拍卖" }]} />
      </div>
    </section>
  );
}
