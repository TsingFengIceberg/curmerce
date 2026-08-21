"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { adminAuthApi } from "@/lib/api/admin-auth";
import { getAdminAccessToken } from "@/lib/auth/storage";

export default function MerchantLoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await adminAuthApi.login({ username: username.trim(), password });
      const permission = await adminAuthApi.getPermissionInfo();
      if (permission.roles?.includes("super_admin")) {
        router.replace("/admin/merchants");
      } else if (permission.roles?.includes("merchant_owner")) {
        router.replace("/merchant/orders");
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

  if (getAdminAccessToken()) {
    router.replace("/merchant/orders");
    return <p className="empty-state">正在进入商家后台…</p>;
  }

  return (
    <section className="auth-layout merchant-login-page">
      <div className="auth-intro">
        <p className="eyebrow">MERCHANT CONSOLE</p>
        <h1>商家履约后台</h1>
        <p>管理员负责平台审核；商家审核通过后会生成独立的店主账号。登录后将按角色进入对应后台。</p>
        <Link className="text-link" href="/login">返回买家登录</Link>
      </div>
      <form className="form-card" onSubmit={submit}>
        <div className="form-card__heading"><div><p className="eyebrow">ADMIN · MERCHANT SIGN IN</p><h2>登录后台</h2></div><span className="form-card__badge">角色分流</span></div>
        {error ? <Notice>{error}</Notice> : null}
        <label className="field"><span>管理员账号</span><input autoComplete="username" minLength={4} onChange={(event) => setUsername(event.target.value)} required value={username} /></label>
        <label className="field"><span>密码</span><input autoComplete="current-password" minLength={4} onChange={(event) => setPassword(event.target.value)} required type="password" value={password} /></label>
        <button className="button button--primary button--full" disabled={busy} type="submit">{busy ? "登录中…" : "登录后台"}</button>
      </form>
    </section>
  );
}
