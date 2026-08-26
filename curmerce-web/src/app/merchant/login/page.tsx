"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { ShieldCheck, Store } from "lucide-react";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { adminAuthApi } from "@/lib/api/admin-auth";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";

function destinationForRoles(roles: string[] | undefined) {
  if (roles?.includes("super_admin")) return "/admin/merchants";
  if (roles?.includes("merchant_owner")) return "/merchant/orders";
  return null;
}

export default function MerchantLoginPage() {
  const router = useRouter();
  const [loginKind, setLoginKind] = useState<"merchant" | "admin">("merchant");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    if (new URLSearchParams(window.location.search).get("role") === "admin") setLoginKind("admin");
    let active = true;

    async function resumeExistingSession() {
      if (!getAdminAccessToken()) {
        if (active) setCheckingSession(false);
        return;
      }
      try {
        const permission = await adminAuthApi.getPermissionInfo();
        if (!active) return;
        const destination = destinationForRoles(permission.roles);
        if (destination) {
          router.replace(destination);
          return;
        }
        await adminAuthApi.logout();
        if (active) {
          setError("该账号未配置平台管理员或商家店主角色，请使用已审核商家的店主账号。");
          setCheckingSession(false);
        }
      } catch {
        clearAdminToken();
        if (active) setCheckingSession(false);
      }
    }

    void resumeExistingSession();
    return () => {
      active = false;
    };
  }, [router]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await adminAuthApi.login({ username: username.trim(), password });
      const permission = await adminAuthApi.getPermissionInfo();
      const destination = destinationForRoles(permission.roles);
      const matchesSelection = (loginKind === "admin" && destination === "/admin/merchants") || (loginKind === "merchant" && destination === "/merchant/orders");
      if (destination && matchesSelection) {
        router.replace(destination);
      } else if (destination) {
        await adminAuthApi.logout();
        setError(loginKind === "admin" ? "这是商家店主账号，请切换到“商家登录”。" : "这是平台管理员账号，请切换到“管理员登录”。");
      } else {
        await adminAuthApi.logout();
        setError("该账号未配置平台管理员或商家店主角色，请使用已审核商家的店主账号。");
      }
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "后台登录失败");
    } finally {
      setBusy(false);
    }
  }

  if (checkingSession) return <p className="empty-state">正在验证后台登录状态…</p>;

  return (
    <section className="auth-layout merchant-login-page">
      <div className="auth-intro">
        <p className="eyebrow">CURMERCE WORKSPACE</p>
        <h1>{loginKind === "merchant" ? "进入商家工作台" : "进入平台管理后台"}</h1>
        <p>{loginKind === "merchant" ? "使用商家审核通过后创建的店主账号，管理商品、订单、活动和售后。" : "平台管理员负责商家与商品审核、交易观察、售后和社区治理。"}</p>
        <Link className="text-link" href="/login">返回买家登录</Link>
      </div>
      <form className="form-card" onSubmit={submit}>
        <div className="workspace-login-switch" role="tablist" aria-label="后台登录身份"><button aria-selected={loginKind === "merchant"} className={loginKind === "merchant" ? "workspace-login-switch__item workspace-login-switch__item--active" : "workspace-login-switch__item"} role="tab" type="button" onClick={() => { setLoginKind("merchant"); setError(null); }}><Store aria-hidden="true" size={18} /><span>商家登录</span></button><button aria-selected={loginKind === "admin"} className={loginKind === "admin" ? "workspace-login-switch__item workspace-login-switch__item--active" : "workspace-login-switch__item"} role="tab" type="button" onClick={() => { setLoginKind("admin"); setError(null); }}><ShieldCheck aria-hidden="true" size={18} /><span>管理员登录</span></button></div>
        <div className="form-card__heading"><div><p className="eyebrow">SECURE WORKSPACE</p><h2>{loginKind === "merchant" ? "商家账号" : "管理员账号"}</h2></div></div>
        {error ? <Notice>{error}</Notice> : null}
        <label className="field"><span>后台用户名</span><input autoComplete="username" minLength={4} onChange={(event) => setUsername(event.target.value)} required value={username} /></label>
        <label className="field"><span>密码</span><input autoComplete="current-password" minLength={4} onChange={(event) => setPassword(event.target.value)} required type="password" value={password} /></label>
        <button className="button button--primary button--full" disabled={busy} type="submit">{busy ? "登录中…" : loginKind === "merchant" ? "进入商家工作台" : "进入平台管理后台"}</button>
      </form>
    </section>
  );
}
