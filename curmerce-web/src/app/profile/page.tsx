"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Notice } from "@/components/notice";
import { AvatarUploader } from "@/components/avatar-uploader";
import { CurmerceApiError } from "@/lib/api/client";
import { memberApi } from "@/lib/api/member";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import type { MemberProfile } from "@/lib/types/api";

type ProfileForm = Pick<MemberProfile, "nickname" | "avatar" | "email" | "sex">;

const emptyForm: ProfileForm = { nickname: "", avatar: "", email: "", sex: 0 };

export default function ProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [form, setForm] = useState<ProfileForm>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void loadProfile();
  }, [router]);

  async function loadProfile() {
    setLoading(true);
    setError(null);
    try {
      const value = await memberApi.getProfile();
      setProfile(value);
      setForm({ nickname: value.nickname ?? "", avatar: value.avatar ?? "", email: value.email ?? "", sex: value.sex ?? 0 });
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "个人资料加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await memberApi.updateProfile({ ...form, avatar: form.avatar?.trim() || undefined, email: form.email?.trim() || undefined });
      setMessage("个人资料已更新");
      await loadProfile();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "个人资料保存失败");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <p className="empty-state">个人资料加载中…</p>;

  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">ACCOUNT · PROFILE</p>
          <h1>个人资料</h1>
          <p>维护商城订单和社区内容使用的公开资料。</p>
        </div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <form className="form-card profile-form" onSubmit={submit}>
        <div className="form-card__heading">
          <div><p className="eyebrow">ACCOUNT SETTINGS</p><h2>基本信息</h2></div>
          <span className="form-card__badge">手机号不可修改</span>
        </div>
        <label className="field"><span>手机号</span><input disabled value={profile?.mobile ?? ""} /></label>
        <label className="field"><span>昵称</span><input required minLength={2} maxLength={30} value={form.nickname} onChange={(event) => setForm({ ...form, nickname: event.target.value })} /></label>
        <label className="field"><span>邮箱</span><input type="email" maxLength={254} value={form.email ?? ""} onChange={(event) => setForm({ ...form, email: event.target.value })} placeholder="可选" /></label>
        <AvatarUploader value={form.avatar} name={form.nickname || profile?.mobile || "用户"} disabled={saving} onChange={(avatar) => setForm({ ...form, avatar })} onError={setError} />
        <label className="field"><span>性别</span><select value={form.sex ?? 0} onChange={(event) => setForm({ ...form, sex: Number(event.target.value) })}><option value={0}>未知</option><option value={1}>男</option><option value={2}>女</option></select></label>
        <button className="button button--primary button--full" disabled={saving} type="submit">{saving ? "保存中…" : "保存资料"}</button>
      </form>
    </section>
  );
}
