"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { CircleDollarSign, Clock3, PackageCheck, Truck } from "lucide-react";
import { DashboardHeader, DashboardMetrics, QuickActions } from "@/components/dashboard-ui";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { personalApi } from "@/lib/api/personal";
import { clearToken, getAccessToken } from "@/lib/auth/storage";

const initial = { drafts: 0, reviewing: 0, selling: 0, shipping: 0 };

export default function PersonalDashboardPage() {
  const router = useRouter();
  const [counts, setCounts] = useState(initial);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) { router.replace("/login"); return; }
    void Promise.all([
      personalApi.page({ pageNo: 1, pageSize: 1, auditStatus: 0 }),
      personalApi.page({ pageNo: 1, pageSize: 1, auditStatus: 1 }),
      personalApi.page({ pageNo: 1, pageSize: 1, auditStatus: 2, saleStatus: 1 }),
      personalApi.orderPage({ pageNo: 1, pageSize: 1, status: 20 }),
    ]).then(([drafts, reviewing, selling, shipping]) => setCounts({ drafts: drafts?.total ?? 0, reviewing: reviewing?.total ?? 0, selling: selling?.total ?? 0, shipping: shipping?.total ?? 0 }))
      .catch((cause) => {
        if (cause instanceof CurmerceApiError && cause.status === 401) { clearToken(); router.replace("/login"); return; }
        setError(cause instanceof CurmerceApiError ? cause.message : "卖家待办加载失败");
      }).finally(() => setLoading(false));
  }, [router]);

  const metrics = [
    { label: "商品草稿", value: counts.drafts, hint: "继续完善并提交", href: "/personal/listings", icon: Clock3 },
    { label: "审核中", value: counts.reviewing, hint: "等待平台反馈", href: "/personal/listings", icon: PackageCheck },
    { label: "正在出售", value: counts.selling, hint: "一件一库存商品", href: "/personal/listings", icon: CircleDollarSign },
    { label: "待发货", value: counts.shipping, hint: "需要填写物流", href: "/personal/orders", icon: Truck },
  ];

  return <section className="workspace-dashboard"><DashboardHeader eyebrow="PERSONAL SELLER" title="卖家概览" description="管理闲置商品，并及时处理已经卖出的订单。" />{error ? <Notice>{error}</Notice> : null}<DashboardMetrics items={metrics} loading={loading} /><QuickActions title="快捷操作" items={[{ href: "/personal/listings/new", label: "发布一件闲置", description: "创建一件一库存的个人商品" }, { href: "/personal/orders", label: "处理卖出订单", description: "查看买家信息并填写物流" }, { href: "/account", label: "返回我的", description: "继续管理买入订单和社区内容" }]} /></section>;
}
