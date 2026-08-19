"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { memberApi } from "@/lib/api/member";

export function SiteHeader() {
  const router = useRouter();
  const loggedIn = Boolean(getAccessToken());

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
          <Link href="/">商城首页</Link>
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
