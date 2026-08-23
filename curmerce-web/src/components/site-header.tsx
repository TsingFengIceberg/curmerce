"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  AUTH_SESSION_CHANGED_EVENT,
  clearAdminToken,
  clearToken,
  getAccessToken,
  getAdminAccessToken,
} from "@/lib/auth/storage";
import { memberApi } from "@/lib/api/member";
import { adminAuthApi } from "@/lib/api/admin-auth";

interface HeaderSession {
  hydrated: boolean;
  buyerLoggedIn: boolean;
  adminLoggedIn: boolean;
  roles: string[];
}

export function SiteHeader() {
  const router = useRouter();
  const [session, setSession] = useState<HeaderSession>({ hydrated: false, buyerLoggedIn: false, adminLoggedIn: false, roles: [] });

  useEffect(() => {
    let requestVersion = 0;

    async function refreshSession() {
      const currentVersion = ++requestVersion;
      const buyerLoggedIn = Boolean(getAccessToken());
      const adminLoggedIn = Boolean(getAdminAccessToken());
      if (!adminLoggedIn) {
        setSession({ hydrated: true, buyerLoggedIn, adminLoggedIn: false, roles: [] });
        return;
      }
      try {
        const permission = await adminAuthApi.getPermissionInfo();
        if (currentVersion !== requestVersion) return;
        setSession({ hydrated: true, buyerLoggedIn, adminLoggedIn: true, roles: permission.roles ?? [] });
      } catch {
        if (currentVersion !== requestVersion) return;
        clearAdminToken();
        setSession({ hydrated: true, buyerLoggedIn, adminLoggedIn: false, roles: [] });
      }
    }

    const handleSessionChanged = () => void refreshSession();
    handleSessionChanged();
    window.addEventListener(AUTH_SESSION_CHANGED_EVENT, handleSessionChanged);
    window.addEventListener("storage", handleSessionChanged);
    return () => {
      requestVersion += 1;
      window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, handleSessionChanged);
      window.removeEventListener("storage", handleSessionChanged);
    };
  }, []);

  const loggedIn = session.hydrated && session.buyerLoggedIn;
  const adminLoggedIn = session.hydrated && session.adminLoggedIn;
  const platformAdmin = adminLoggedIn && session.roles.includes("super_admin");
  const merchantOwner = adminLoggedIn && session.roles.includes("merchant_owner");

  async function logout() {
    try {
      if (loggedIn) await memberApi.logout();
      if (adminLoggedIn) await adminAuthApi.logout();
    } finally {
      clearToken();
      clearAdminToken();
      router.push("/login");
      router.refresh();
    }
  }

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Link className="brand" href="/">
          <span className="brand__mark">C</span>
          <span>Curmerce</span>
        </Link>
        <nav className="site-nav" aria-label="主导航">
          <Link href="/catalog">商城首页</Link>
          <Link href="/releases">限时发售</Link>
          <Link href="/auctions">拍卖</Link>
          <Link href="/community">社区</Link>
          <Link href="/cart">购物车</Link>
          {loggedIn ? <Link href="/orders">我的订单</Link> : null}
          {loggedIn ? <Link href="/profile">个人资料</Link> : null}
          {loggedIn ? <Link href="/refunds">退款中心</Link> : null}
          {loggedIn ? <Link href="/personal/listings">我的闲置</Link> : null}
          {loggedIn ? <Link href="/community/following">关注 Feed</Link> : null}
          {loggedIn ? <Link href="/community/favorites">我的收藏</Link> : null}
          {loggedIn ? <Link href="/personal/orders">卖家发货</Link> : null}
          {platformAdmin ? (
            <>
              <Link href="/admin/merchants">商家审核</Link>
              <Link href="/admin/orders">平台订单</Link>
              <Link href="/admin/refunds">平台退款</Link>
              <Link href="/admin/community">社区审核</Link>
            </>
          ) : null}
          {merchantOwner ? (
            <>
              <Link href="/merchant/orders">订单管理</Link>
              <Link href="/merchant/store">店铺资料</Link>
              <Link href="/merchant/products">商品管理</Link>
              <Link href="/merchant/releases">限时发售</Link>
              <Link href="/merchant/auctions">拍卖管理</Link>
              <Link href="/merchant/refunds">退款处理</Link>
            </>
          ) : null}
          {session.hydrated && !adminLoggedIn ? <Link href="/merchant/login">后台登录</Link> : null}
          {loggedIn ? <Link href="/addresses">收货地址</Link> : null}
          {session.hydrated ? (
            loggedIn ? (
              <button className="link-button" type="button" onClick={logout}>
                退出登录
              </button>
            ) : (
              <Link href="/login">登录</Link>
            )
          ) : null}
        </nav>
      </div>
    </header>
  );
}
