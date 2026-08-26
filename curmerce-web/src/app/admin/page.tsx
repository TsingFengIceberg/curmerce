"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ClipboardCheck, MessageSquareWarning, RotateCcw, Store } from "lucide-react";
import { DashboardHeader, DashboardMetrics, QuickActions } from "@/components/dashboard-ui";
import { Notice } from "@/components/notice";
import { adminMerchantApi } from "@/lib/api/admin-merchant";
import { adminProductApi } from "@/lib/api/admin-product";
import { adminRefundApi } from "@/lib/api/admin-refund";
import { adminCommunityApi } from "@/lib/api/community";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";

const initial = { merchants: 0, products: 0, refunds: 0, reports: 0 };

export default function AdminDashboardPage() {
  const router = useRouter();
  const [counts, setCounts] = useState(initial);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAdminAccessToken()) { router.replace("/merchant/login"); return; }
    void Promise.all([
      adminMerchantApi.page({ pageNo: 1, pageSize: 1, status: 0 }),
      adminProductApi.reviewPage({ pageNo: 1, pageSize: 1, auditStatus: 1 }),
      adminRefundApi.page({ pageNo: 1, pageSize: 1, status: 10 }),
      adminCommunityApi.reports({ pageNo: 1, pageSize: 1, status: 0 }),
    ]).then(([merchants, products, refunds, reports]) => setCounts({ merchants: merchants?.total ?? 0, products: products?.total ?? 0, refunds: refunds?.total ?? 0, reports: reports?.total ?? 0 }))
      .catch((cause) => {
        if (cause instanceof CurmerceApiError && cause.status === 401) { clearAdminToken(); router.replace("/merchant/login"); return; }
        setError(cause instanceof CurmerceApiError ? cause.message : "平台待办加载失败");
      }).finally(() => setLoading(false));
  }, [router]);

  const metrics = [
    { label: "待审核商家", value: counts.merchants, hint: "处理入驻申请", href: "/admin/merchants", icon: Store },
    { label: "待审核商品", value: counts.products, hint: "检查商品与 SKU", href: "/admin/product-review", icon: ClipboardCheck },
    { label: "待处理退款", value: counts.refunds, hint: "跟进平台售后", href: "/admin/refunds", icon: RotateCcw },
    { label: "待处理举报", value: counts.reports, hint: "维护社区内容秩序", href: "/admin/community", icon: MessageSquareWarning },
  ];

  return <section className="workspace-dashboard"><DashboardHeader eyebrow="PLATFORM OVERVIEW" title="平台工作台" description="集中处理需要平台介入的审核、售后和社区治理任务。" />{error ? <Notice>{error}</Notice> : null}<DashboardMetrics items={metrics} loading={loading} /><QuickActions title="常用管理" items={[{ href: "/admin/orders", label: "查看平台订单", description: "按状态或订单号追踪全平台交易" }, { href: "/admin/categories", label: "维护商品分类", description: "管理分类层级、启停与排序" }, { href: "/admin/community", label: "进入社区治理", description: "结合帖子上下文处理举报" }]} /></section>;
}
