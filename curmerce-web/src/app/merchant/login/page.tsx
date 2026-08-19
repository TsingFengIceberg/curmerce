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

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    void adminAuthApi.login({ username: username.trim(), password }).then(() => {
      router.replace("/merchant/orders");
    }).catch((cause) => {
      setError(cause instanceof CurmerceApiError ? cause.message : "后台登录失败");
    }).finally(() => setBusy(false));
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
        <p>使用管理后台账号登录后，只能查看当前商家上下文中的待发货订单，并提交物流信息。</p>
        <Link className="text-link" href="/login">返回买家登录</Link>
      </div>
      <form className="form-card" onSubmit={submit}>
        <div className="form-card__heading"><div><p className="eyebrow">ADMIN · SIGN IN</p><h2>登录管理后台</h2></div><span className="form-card__badge">权限隔离</span></div>
        {error ? <Notice>{error}</Notice> : null}
        <label className="field"><span>管理员账号</span><input autoComplete="username" minLength={4} onChange={(event) => setUsername(event.target.value)} required value={username} /></label>
        <label className="field"><span>密码</span><input autoComplete="current-password" minLength={4} onChange={(event) => setPassword(event.target.value)} required type="password" value={password} /></label>
        <button className="button button--primary button--full" disabled={busy} type="submit">{busy ? "登录中…" : "登录商家后台"}</button>
      </form>
    </section>
  );
}
