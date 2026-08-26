"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { memberApi } from "@/lib/api/member";
import { CurmerceApiError } from "@/lib/api/client";
import { saveToken } from "@/lib/auth/storage";
import { safeReturnTo } from "@/lib/auth/guards";
import { Notice } from "@/components/notice";

export default function LoginPage() {
  const router = useRouter();
  const [registered, setRegistered] = useState(false);
  const [returnTo, setReturnTo] = useState("/catalog");
  const [mobile, setMobile] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    setRegistered(params.get("registered") === "1");
    setReturnTo(safeReturnTo(params.get("returnTo") ?? window.sessionStorage.getItem("curmerce.app-return-to"), "/catalog"));
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const token = await memberApi.login({ mobile, password });
      saveToken(token);
      window.sessionStorage.removeItem("curmerce.app-return-to");
      router.replace(returnTo);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "登录失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-layout">
      <div className="auth-intro">
        <p className="eyebrow">WELCOME BACK</p>
        <h1>登录 Curmerce</h1>
        <p>登录后可以维护收货地址，并继续浏览商品、加入购物车和创建订单。</p>
      </div>
      <form className="form-card" onSubmit={submit}>
        <div className="form-card__heading">
          <div>
            <p className="eyebrow">买家账号</p>
            <h2>手机号登录</h2>
          </div>
          <span className="form-card__badge">APP</span>
        </div>
        {registered ? <Notice tone="success">注册成功，请使用刚创建的账号登录。</Notice> : null}
        {error ? <Notice>{error}</Notice> : null}
        <label className="field">
          <span>手机号</span>
          <input required inputMode="tel" value={mobile} onChange={(event) => setMobile(event.target.value)} placeholder="请输入手机号" />
        </label>
        <label className="field">
          <span>密码</span>
          <input required minLength={8} type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="至少 8 位" />
        </label>
        <button className="button button--primary button--full" disabled={submitting} type="submit">
          {submitting ? "登录中…" : "登录"}
        </button>
        <p className="form-card__footer">
          还没有账号？ <Link href="/register">立即注册</Link>
        </p>
      </form>
    </section>
  );
}
