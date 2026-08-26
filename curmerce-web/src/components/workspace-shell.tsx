"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Boxes,
  ChevronRight,
  CircleDollarSign,
  CircleUserRound,
  ClipboardCheck,
  FileText,
  Heart,
  Gavel,
  LayoutDashboard,
  MessageSquareWarning,
  PackageCheck,
  ReceiptText,
  RotateCcw,
  Settings2,
  ShoppingBag,
  Store,
  Tags,
  Timer,
  Truck,
  UsersRound,
} from "lucide-react";
import { adminMerchantApi } from "@/lib/api/admin-merchant";
import { adminOrderApi } from "@/lib/api/admin-order";
import { adminProductApi } from "@/lib/api/admin-product";
import { adminRefundApi } from "@/lib/api/admin-refund";
import { adminCommunityApi } from "@/lib/api/community";
import { orderApi } from "@/lib/api/order";
import { personalApi } from "@/lib/api/personal";
import { getAccessToken, getAdminAccessToken } from "@/lib/auth/storage";

type WorkspaceKind = "admin" | "merchant" | "personal" | "buyer";

const workspaceMeta = {
  buyer: {
    title: "我的 Curmerce",
    description: "订单、售后与账户设置",
    root: "/account",
    navigation: [
      { href: "/account", label: "我的首页", icon: LayoutDashboard },
      { href: "/orders", label: "买入订单", icon: ReceiptText },
      { href: "/refunds", label: "退款中心", icon: RotateCcw },
      { href: "/account/product-favorites", label: "商品收藏", icon: Heart },
      { href: "/profile", label: "个人资料", icon: CircleUserRound },
      { href: "/addresses", label: "收货地址", icon: Store },
    ],
  },
  admin: {
    title: "平台管理",
    description: "审核、治理与全平台交易观察",
    root: "/admin",
    navigation: [
      { href: "/admin", label: "工作台", icon: LayoutDashboard },
      { href: "/admin/merchants", label: "商家入驻", icon: Store },
      { href: "/admin/product-review", label: "商品审核", icon: ClipboardCheck },
      { href: "/admin/categories", label: "商品分类", icon: Tags },
      { href: "/admin/orders", label: "平台订单", icon: ReceiptText },
      { href: "/admin/refunds", label: "退款审核", icon: RotateCcw },
      { href: "/admin/community", label: "社区治理", icon: MessageSquareWarning },
    ],
  },
  merchant: {
    title: "商家工作台",
    description: "商品、履约与售后经营管理",
    root: "/merchant",
    navigation: [
      { href: "/merchant", label: "经营概览", icon: LayoutDashboard },
      { href: "/merchant/products", label: "商品管理", icon: Boxes },
      { href: "/merchant/orders", label: "订单履约", icon: Truck },
      { href: "/merchant/refunds", label: "退款处理", icon: RotateCcw },
      { href: "/merchant/releases", label: "限时发售", icon: Timer },
      { href: "/merchant/auctions", label: "拍卖管理", icon: Gavel },
      { href: "/merchant/store", label: "店铺设置", icon: Settings2 },
    ],
  },
  personal: {
    title: "个人卖家中心",
    description: "管理闲置商品和卖出订单",
    root: "/personal",
    navigation: [
      { href: "/personal", label: "卖家概览", icon: LayoutDashboard },
      { href: "/personal/listings", label: "我的闲置", icon: ShoppingBag },
      { href: "/personal/orders", label: "卖出订单", icon: PackageCheck },
    ],
  },
} as const;

function matches(pathname: string, href: string, root: string) {
  if (href === root) return pathname === root;
  return pathname === href || pathname.startsWith(`${href}/`);
}

function currentLabel(kind: WorkspaceKind, pathname: string) {
  const meta = workspaceMeta[kind];
  return meta.navigation.find((item) => matches(pathname, item.href, meta.root))?.label ?? meta.title;
}

async function loadTodoBadges(kind: WorkspaceKind): Promise<Record<string, number>> {
  if (kind === "buyer") {
    if (!getAccessToken()) return {};
    const [pendingPayment, shipped] = await Promise.all([
      orderApi.page({ pageNo: 1, pageSize: 1, status: 10 }),
      orderApi.page({ pageNo: 1, pageSize: 1, status: 30 }),
    ]);
    return { "/orders": (pendingPayment.total ?? 0) + (shipped.total ?? 0) };
  }
  if (kind === "personal") {
    if (!getAccessToken()) return {};
    const shipping = await personalApi.orderPage({ pageNo: 1, pageSize: 1, status: 20 });
    return { "/personal/orders": shipping.total ?? 0 };
  }
  if (!getAdminAccessToken()) return {};
  if (kind === "merchant") {
    const [shipping, refunds] = await Promise.all([
      adminOrderApi.pageOwn({ pageNo: 1, pageSize: 1, status: 20 }),
      adminRefundApi.pageOwn({ pageNo: 1, pageSize: 1, status: 10 }),
    ]);
    return { "/merchant/orders": shipping.total ?? 0, "/merchant/refunds": refunds.total ?? 0 };
  }
  const [merchants, products, refunds, reports] = await Promise.all([
    adminMerchantApi.page({ pageNo: 1, pageSize: 1, status: 0 }),
    adminProductApi.reviewPage({ pageNo: 1, pageSize: 1, auditStatus: 1 }),
    adminRefundApi.page({ pageNo: 1, pageSize: 1, status: 10 }),
    adminCommunityApi.reports({ pageNo: 1, pageSize: 1, status: 0 }),
  ]);
  return {
    "/admin/merchants": merchants.total ?? 0,
    "/admin/product-review": products.total ?? 0,
    "/admin/refunds": refunds.total ?? 0,
    "/admin/community": reports.total ?? 0,
  };
}

export function WorkspaceShell({ kind, children }: { kind: WorkspaceKind; children: React.ReactNode }) {
  const pathname = usePathname();
  const [badges, setBadges] = useState<Record<string, number>>({});

  useEffect(() => {
    let active = true;
    void loadTodoBadges(kind).then((nextBadges) => {
      if (active) setBadges(nextBadges);
    }).catch(() => {
      if (active) setBadges({});
    });
    return () => {
      active = false;
    };
  }, [kind, pathname]);

  if (pathname === "/merchant/login") return children;
  const meta = workspaceMeta[kind];

  return (
    <div className={`workspace-shell workspace-shell--${kind}`}>
      <aside className="workspace-sidebar">
        <div className="workspace-sidebar__heading">
          <span className="workspace-sidebar__icon">{kind === "admin" ? <UsersRound aria-hidden="true" /> : kind === "merchant" ? <CircleDollarSign aria-hidden="true" /> : kind === "buyer" ? <CircleUserRound aria-hidden="true" /> : <FileText aria-hidden="true" />}</span>
          <div><strong>{meta.title}</strong><small>{meta.description}</small></div>
        </div>
        <nav className="workspace-nav" aria-label={`${meta.title}导航`}>
          {meta.navigation.map(({ href, label, icon: Icon }) => (
            <Link className={matches(pathname, href, meta.root) ? "workspace-nav__item workspace-nav__item--active" : "workspace-nav__item"} href={href} key={href}>
              <Icon aria-hidden="true" size={18} /><span>{label}</span><span className="workspace-nav__badge-slot">{badges[href] > 0 ? <span aria-label={`${badges[href]} 项待办`} className="workspace-nav__badge">{badges[href] > 99 ? "99+" : badges[href]}</span> : null}</span>{matches(pathname, href, meta.root) ? <ChevronRight className="workspace-nav__arrow" aria-hidden="true" size={16} /> : <span aria-hidden="true" />}
            </Link>
          ))}
        </nav>
      </aside>
      <div className="workspace-content">
        <div className="workspace-breadcrumb"><Link href={meta.root}>{meta.title}</Link><ChevronRight aria-hidden="true" size={14} /><span>{currentLabel(kind, pathname)}</span></div>
        {children}
      </div>
    </div>
  );
}
