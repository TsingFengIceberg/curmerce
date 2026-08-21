"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { adminAuthApi } from "@/lib/api/admin-auth";
import { adminStoreApi } from "@/lib/api/admin-product";
import { clearAdminToken } from "@/lib/auth/storage";
import type { StoreSummary } from "@/lib/types/api";
import { ensureMerchantOwner } from "@/lib/auth/guards";

export default function MerchantStorePage() {
  const router = useRouter();
  const [store, setStore] = useState<StoreSummary | null>(null);
  const [form, setForm] = useState({ name: "", description: "", contactName: "", contactMobile: "" });
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { void ensureMerchantOwner(router).then((allowed) => { if (allowed) void load(); }); }, [router]);
  async function load() { try { const current = await adminStoreApi.own(); setStore(current); setForm({ name: current.name, description: current.description ?? "", contactName: current.contactName ?? "", contactMobile: current.contactMobile ?? "" }); } catch (cause) { handleError(cause, "店铺信息加载失败"); } }
  function handleError(cause: unknown, fallback: string) { if (cause instanceof CurmerceApiError && cause.status === 401) { clearAdminToken(); window.location.href = "/merchant/login"; return; } setError(cause instanceof CurmerceApiError ? cause.message : fallback); }
  async function save(event: React.FormEvent<HTMLFormElement>) { event.preventDefault(); setBusy(true); setError(null); setMessage(null); try { await adminStoreApi.updateOwn(form); setMessage("店铺资料已保存"); await load(); } catch (cause) { handleError(cause, "店铺资料保存失败"); } finally { setBusy(false); } }
  async function logout() { await adminAuthApi.logout(); window.location.href = "/merchant/login"; }
  return <section className="content-section admin-page"><div className="section-heading"><div><p className="eyebrow">MERCHANT · STORE</p><h1>店铺资料</h1><p>只能修改当前登录商家自己的店铺资料。</p></div><div className="inline-actions"><Link className="button button--secondary" href="/merchant/products">商品管理</Link><Link className="button button--secondary" href="/merchant/orders">待发货订单</Link><button className="button button--secondary" type="button" onClick={() => void logout()}>退出后台</button></div></div>{message ? <Notice tone="success">{message}</Notice> : null}{error ? <Notice>{error}</Notice> : null}<form className="orders-panel admin-form-panel" onSubmit={save}><div className="form-readonly">店铺编码：{store?.code ?? "加载中…"}</div><label className="field"><span>店铺名称</span><input required minLength={2} maxLength={64} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label><label className="field"><span>店铺简介</span><textarea maxLength={500} rows={5} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><label className="field"><span>联系人</span><input required minLength={2} maxLength={30} value={form.contactName} onChange={(event) => setForm({ ...form, contactName: event.target.value })} /></label><label className="field"><span>联系电话</span><input required minLength={11} maxLength={11} pattern="1[3-9][0-9]{9}" value={form.contactMobile} onChange={(event) => setForm({ ...form, contactMobile: event.target.value })} /></label><button className="button button--primary" disabled={busy} type="submit">{busy ? "保存中…" : "保存店铺资料"}</button></form></section>;
}
