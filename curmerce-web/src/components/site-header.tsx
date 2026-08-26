"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  CircleUserRound,
  Compass,
  Gavel,
  LayoutDashboard,
  LogIn,
  LogOut,
  Menu,
  ShoppingBag,
  ShoppingCart,
  Store,
  Timer,
  X,
} from "lucide-react";
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

const publicLinks = [
  { href: "/community", label: "发现", icon: Compass },
  { href: "/catalog", label: "商城", icon: Store },
  { href: "/releases", label: "限时发售", icon: Timer },
  { href: "/auctions", label: "拍卖", icon: Gavel },
];

function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);
  const [session, setSession] = useState<HeaderSession>({ hydrated: false, buyerLoggedIn: false, adminLoggedIn: false, roles: [] });
  const backend = pathname.startsWith("/admin") || (pathname.startsWith("/merchant") && pathname !== "/merchant/login");

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

  useEffect(() => setMenuOpen(false), [pathname]);

  const loggedIn = session.hydrated && session.buyerLoggedIn;
  const adminLoggedIn = session.hydrated && session.adminLoggedIn;
  const platformAdmin = adminLoggedIn && session.roles.includes("super_admin");
  const merchantOwner = adminLoggedIn && session.roles.includes("merchant_owner");
  const workspaceHref = platformAdmin ? "/admin" : merchantOwner ? "/merchant" : "/merchant/login";
  const workspaceLabel = platformAdmin ? "平台管理" : merchantOwner ? "商家工作台" : "工作台";

  async function logoutBuyer() {
    try {
      if (loggedIn) await memberApi.logout();
    } finally {
      clearToken();
      router.push("/login");
      router.refresh();
    }
  }

  async function logoutWorkspace() {
    try {
      if (adminLoggedIn) await adminAuthApi.logout();
    } finally {
      clearAdminToken();
      router.push("/merchant/login");
      router.refresh();
    }
  }

  if (backend) {
    return (
      <header className="site-header site-header--workspace">
        <div className="site-header__inner">
          <Link className="brand" href={workspaceHref}>
            <span className="brand__mark">C</span>
            <span>Curmerce</span>
            <span className="brand__context">{workspaceLabel}</span>
          </Link>
          <nav className="workspace-header-actions" aria-label="工作区操作">
            <Link className="header-action" href="/catalog"><ShoppingBag aria-hidden="true" size={17} />返回商城</Link>
            <button className="header-action" type="button" onClick={() => void logoutWorkspace()}><LogOut aria-hidden="true" size={17} />退出工作台</button>
          </nav>
        </div>
      </header>
    );
  }

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Link className="brand" href="/"><span className="brand__mark">C</span><span>Curmerce</span></Link>
        <button className="mobile-menu-button" type="button" aria-expanded={menuOpen} aria-label={menuOpen ? "关闭导航" : "打开导航"} onClick={() => setMenuOpen((current) => !current)}>
          {menuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
        </button>
        <div className={`site-navigation${menuOpen ? " site-navigation--open" : ""}`}>
          <nav className="site-nav" aria-label="主导航">
            {publicLinks.map(({ href, label, icon: Icon }) => <Link className={isActive(pathname, href) ? "site-nav__link site-nav__link--active" : "site-nav__link"} href={href} key={href}><Icon aria-hidden="true" size={17} />{label}</Link>)}
          </nav>
          <nav className="site-actions" aria-label="账户导航">
            <Link className={isActive(pathname, "/cart") ? "icon-link icon-link--active" : "icon-link"} href="/cart" title="购物车"><ShoppingCart aria-hidden="true" size={20} /><span className="mobile-only-label">购物车</span></Link>
            {session.hydrated ? loggedIn ? (
              <>
                <Link className={isActive(pathname, "/account") ? "header-action header-action--active" : "header-action"} href="/account"><CircleUserRound aria-hidden="true" size={18} />我的</Link>
                <button aria-label="退出用户账号" className="icon-link buyer-session-logout" title="退出用户账号" type="button" onClick={() => void logoutBuyer()}><LogOut aria-hidden="true" size={18} /><span className="mobile-only-label">退出用户账号</span></button>
              </>
            ) : <Link className="header-action" href="/login"><LogIn aria-hidden="true" size={18} />登录</Link> : null}
            <Link className="workspace-entry" href={workspaceHref}><LayoutDashboard aria-hidden="true" size={17} />{workspaceLabel}</Link>
          </nav>
        </div>
      </div>
    </header>
  );
}
