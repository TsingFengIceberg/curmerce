"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { memberApi } from "@/lib/api/member";
import { CurmerceApiError } from "@/lib/api/client";
import { Notice } from "@/components/notice";

export default function RegisterPage() {
  const router = useRouter();
  const [form, setForm] = useState({ mobile: "", password: "", nickname: "" });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await memberApi.register(form);
      router.push("/login?registered=1");
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "注册失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-layout">
      <div className="auth-intro">
        <p className="eyebrow">JOIN THE COMMUNITY</p>
        <h1>创建买家账号</h1>
        <p>先从一个真实可用的账号开始，后续商品发现、订单与社区能力都会接入这个身份。</p>
      </div>
      <form className="form-card" onSubmit={submit}>
        <div className="form-card__heading">
          <div>
            <p className="eyebrow">新用户</p>
            <h2>注册 Curmerce</h2>
          </div>
          <span className="form-card__badge">APP</span>
        </div>
        {error ? <Notice>{error}</Notice> : null}
        <label className="field">
          <span>昵称</span>
          <input required minLength={2} maxLength={30} value={form.nickname} onChange={(event) => update("nickname", event.target.value)} placeholder="例如：山川旅人" />
        </label>
        <label className="field">
          <span>手机号</span>
          <input required inputMode="tel" value={form.mobile} onChange={(event) => update("mobile", event.target.value)} placeholder="请输入手机号" />
        </label>
        <label className="field">
          <span>密码</span>
          <input required minLength={8} type="password" value={form.password} onChange={(event) => update("password", event.target.value)} placeholder="至少 8 位" />
        </label>
        <button className="button button--primary button--full" disabled={submitting} type="submit">
          {submitting ? "创建中…" : "创建账号"}
        </button>
        <p className="form-card__footer">
          已经有账号？ <Link href="/login">返回登录</Link>
        </p>
      </form>
    </section>
  );
}
