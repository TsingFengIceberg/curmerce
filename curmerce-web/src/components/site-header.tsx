"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { clearToken, getAccessToken, getAdminAccessToken } from "@/lib/auth/storage";
import { memberApi } from "@/lib/api/member";

export function SiteHeader() {
  const router = useRouter();
  const loggedIn = Boolean(getAccessToken());
  const adminLoggedIn = Boolean(getAdminAccessToken());

  async function logout() {
    try {
      if (loggedIn) await memberApi.logout();
    } finally {
      clearToken();
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
          <Link href="/cart">购物车</Link>
          {loggedIn ? <Link href="/orders">我的订单</Link> : null}
          {loggedIn ? <Link href="/refunds">退款中心</Link> : null}
          {adminLoggedIn ? (
            <>
              <Link href="/merchant/orders">待发货订单</Link>
              <Link href="/merchant/products">商品管理</Link>
              <Link href="/merchant/refunds">退款审核</Link>
            </>
          ) : <Link href="/merchant/login">商家后台</Link>}
          <Link href="/addresses">收货地址</Link>
          {loggedIn ? (
            <button className="link-button" type="button" onClick={logout}>
              退出登录
            </button>
          ) : (
            <Link href="/login">登录</Link>
          )}
        </nav>
      </div>
    </header>
  );
}
