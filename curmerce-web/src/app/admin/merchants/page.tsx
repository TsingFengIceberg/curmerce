"use client";

import { Building2, CheckCircle2, Eye, Plus, Search, ShieldCheck, XCircle } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { adminMerchantApi } from "@/lib/api/admin-merchant";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMerchantStatus } from "@/lib/format";
import type { MerchantSummary } from "@/lib/types/api";

const PAGE_SIZE = 15;
const emptyMerchant = { name: "", code: "", contactName: "", contactMobile: "", defaultStoreName: "", defaultStoreCode: "" };
const emptyAccount = { username: "", nickname: "", password: "" };
type ReviewMode = "approve" | "reject";

export default function AdminMerchantsPage() {
  const router = useRouter();
  const [merchants, setMerchants] = useState<MerchantSummary[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState("");
  const [name, setName] = useState("");
  const [nameInput, setNameInput] = useState("");
  const [detail, setDetail] = useState<MerchantSummary | null>(null);
  const [creating, setCreating] = useState(false);
  const [createForm, setCreateForm] = useState(emptyMerchant);
  const [reviewTarget, setReviewTarget] = useState<MerchantSummary | null>(null);
  const [reviewMode, setReviewMode] = useState<ReviewMode>("approve");
  const [account, setAccount] = useState(emptyAccount);
  const [reason, setReason] = useState("");
  const [confirmReview, setConfirmReview] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    const initialName = new URLSearchParams(window.location.search).get("name") ?? "";
    setNameInput(initialName);
    setName(initialName);
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    if (!getAdminAccessToken()) { router.replace("/merchant/login"); return; }
    void load();
  }, [hydrated, router, pageNo, status, name]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await adminMerchantApi.page({ pageNo, pageSize: PAGE_SIZE, status: status ? Number(status) : undefined, name });
      setMerchants(page.list ?? []);
      setTotal(page.total ?? 0);
    } catch (cause) {
      handle(cause, "商家列表加载失败");
    } finally {
      setLoading(false);
    }
  }

  function handle(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      router.replace("/merchant/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNo(1);
    setName(nameInput.trim());
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await adminMerchantApi.create(createForm);
      setMessage("商家入驻申请已创建");
      setCreateForm(emptyMerchant);
      setCreating(false);
      setPageNo(1);
      await load();
    } catch (cause) {
      handle(cause, "创建商家申请失败");
    } finally {
      setBusy(false);
    }
  }

  function openReview(merchant: MerchantSummary, mode: ReviewMode) {
    setDetail(null);
    setReviewTarget(merchant);
    setReviewMode(mode);
    setAccount(emptyAccount);
    setReason("");
    setError(null);
  }

  function validateReview() {
    if (reviewMode === "reject" && (reason.trim().length < 2 || reason.trim().length > 255)) {
      setError("拒绝原因需要填写 2 至 255 个字符");
      return false;
    }
    if (reviewMode === "approve" && (!/^[a-zA-Z0-9]{4,30}$/.test(account.username) || account.nickname.trim().length < 2 || account.nickname.trim().length > 30 || account.password.length < 8 || account.password.length > 64)) {
      setError("主账号用户名需为 4 至 30 位字母或数字，昵称 2 至 30 位，初始密码 8 至 64 位");
      return false;
    }
    return true;
  }

  async function review() {
    if (!reviewTarget || !validateReview()) return;
    setConfirmReview(false);
    setBusy(true);
    setError(null);
    try {
      if (reviewMode === "approve") await adminMerchantApi.approve({ id: reviewTarget.id, ...account, nickname: account.nickname.trim() });
      else await adminMerchantApi.reject({ id: reviewTarget.id, reason: reason.trim() });
      setMessage(reviewMode === "approve" ? "商家已通过审核，后台主账号已创建" : "商家申请已拒绝");
      setReviewTarget(null);
      await load();
    } catch (cause) {
      handle(cause, "商家审核失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="content-section admin-page">
      <div className="section-heading"><div><p className="eyebrow">ADMIN · MERCHANTS</p><h1>商家入驻</h1><p>查看入驻资料、处理审核，并为通过的商家初始化后台主账号。</p></div><button className="button button--primary button--icon-label" type="button" onClick={() => { setCreateForm(emptyMerchant); setCreating(true); }}><Plus aria-hidden="true" size={17} />创建申请</button></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="workspace-section admin-data-panel">
        <div className="admin-data-toolbar"><select aria-label="商家审核状态" value={status} onChange={(event) => { setStatus(event.target.value); setPageNo(1); }}><option value="">全部状态</option><option value="0">待审核</option><option value="1">已通过</option><option value="2">已拒绝</option></select><form className="order-search" onSubmit={search}><Search aria-hidden="true" size={16} /><input aria-label="商家名称" placeholder="搜索商家名称" value={nameInput} onChange={(event) => setNameInput(event.target.value)} /><button type="submit">查询</button></form><span>共 {total} 家</span></div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !merchants.length ? <EmptyState icon={<Building2 aria-hidden="true" size={23} />} title="没有符合条件的商家" description="调整筛选条件或创建新的入驻申请。" actionLabel="创建申请" onAction={() => setCreating(true)} /> : null}
        {!loading && merchants.length ? <div className="admin-merchant-table"><div className="admin-merchant-table__head"><span>商家与店铺</span><span>联系人</span><span>申请时间</span><span>审核状态</span><span>操作</span></div>{merchants.map((merchant) => <article className="admin-merchant-table__row" key={merchant.id}><div className="admin-merchant-table__identity"><span className="admin-table-avatar"><Building2 aria-hidden="true" size={18} /></span><div><strong>{merchant.name}</strong><small>{merchant.defaultStoreName}</small></div></div><div className="admin-table-stack"><strong>{merchant.contactName}</strong><small>{merchant.contactMobile}</small></div><span className="admin-table-time">{formatDateTime(merchant.createTime)}</span><span className={`tag merchant-status merchant-status--${merchant.status}`}>{formatMerchantStatus(merchant.status)}</span><div className="listing-table__actions"><button aria-label={`查看 ${merchant.name}`} className="icon-button" title="查看资料" type="button" onClick={() => setDetail(merchant)}><Eye aria-hidden="true" size={16} /></button>{merchant.status === 0 ? <button aria-label={`通过 ${merchant.name}`} className="icon-button" title="审核通过" type="button" onClick={() => openReview(merchant, "approve")}><CheckCircle2 aria-hidden="true" size={16} /></button> : null}{merchant.status === 0 ? <button aria-label={`拒绝 ${merchant.name}`} className="icon-button icon-button--danger" title="拒绝申请" type="button" onClick={() => openReview(merchant, "reject")}><XCircle aria-hidden="true" size={16} /></button> : null}</div></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>

      <Drawer open={creating} title="创建商家入驻申请" description="录入主体、联系人和首个店铺信息，创建后进入待审核队列。" busy={busy} onClose={() => setCreating(false)}><form className="drawer-form" onSubmit={create}><label className="field"><span>商家名称</span><input minLength={2} maxLength={64} required value={createForm.name} onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))} /></label><label className="field"><span>商家识别码</span><input minLength={3} maxLength={32} pattern="[a-z][a-z0-9_]{2,31}" placeholder="例如 autumn_hobby" required value={createForm.code} onChange={(event) => setCreateForm((current) => ({ ...current, code: event.target.value }))} /><small className="field-help">用于系统内唯一识别，创建后不建议修改。</small></label><div className="admin-form-grid"><label className="field"><span>联系人</span><input minLength={2} maxLength={30} required value={createForm.contactName} onChange={(event) => setCreateForm((current) => ({ ...current, contactName: event.target.value }))} /></label><label className="field"><span>联系电话</span><input maxLength={11} pattern="1[3-9][0-9]{9}" required value={createForm.contactMobile} onChange={(event) => setCreateForm((current) => ({ ...current, contactMobile: event.target.value }))} /></label></div><label className="field"><span>首个店铺名称</span><input minLength={2} maxLength={64} required value={createForm.defaultStoreName} onChange={(event) => setCreateForm((current) => ({ ...current, defaultStoreName: event.target.value }))} /></label><label className="field"><span>店铺识别码</span><input minLength={3} maxLength={32} pattern="[a-z][a-z0-9_]{2,31}" placeholder="例如 autumn_store" required value={createForm.defaultStoreCode} onChange={(event) => setCreateForm((current) => ({ ...current, defaultStoreCode: event.target.value }))} /></label><div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setCreating(false)}>取消</button><button className="button button--primary" disabled={busy} type="submit">{busy ? "创建中…" : "创建申请"}</button></div></form></Drawer>

      <Drawer open={Boolean(detail)} title="商家申请详情" description={detail ? detail.name : ""} onClose={() => setDetail(null)}>{detail ? <div className="drawer-form"><div className="detail-rows"><div><span>审核状态</span><strong>{formatMerchantStatus(detail.status)}</strong></div><div><span>商家名称</span><strong>{detail.name}</strong></div><div><span>商家识别码</span><strong>{detail.code}</strong></div><div><span>首个店铺</span><strong>{detail.defaultStoreName}</strong></div><div><span>店铺识别码</span><strong>{detail.defaultStoreCode}</strong></div><div><span>联系人</span><strong>{detail.contactName} · {detail.contactMobile}</strong></div><div><span>申请时间</span><strong>{formatDateTime(detail.createTime)}</strong></div>{detail.reviewTime ? <div><span>审核时间</span><strong>{formatDateTime(detail.reviewTime)}</strong></div> : null}{detail.rejectReason ? <div><span>拒绝原因</span><strong>{detail.rejectReason}</strong></div> : null}</div>{detail.status === 0 ? <div className="drawer-form__actions"><button className="button button--danger button--icon-label" type="button" onClick={() => openReview(detail, "reject")}><XCircle aria-hidden="true" size={16} />拒绝申请</button><button className="button button--primary button--icon-label" type="button" onClick={() => openReview(detail, "approve")}><ShieldCheck aria-hidden="true" size={16} />通过审核</button></div> : null}</div> : null}</Drawer>

      <Drawer open={Boolean(reviewTarget)} title={reviewMode === "approve" ? "通过商家审核" : "拒绝商家申请"} description={reviewTarget ? `${reviewTarget.name} · ${reviewTarget.defaultStoreName}` : ""} busy={busy} onClose={() => setReviewTarget(null)}>{reviewTarget ? <div className="drawer-form">{reviewMode === "approve" ? <><Notice tone="info">审核通过后将创建商家后台主账号，商家可使用该账号管理商品、订单和售后。</Notice><label className="field"><span>主账号用户名</span><input minLength={4} maxLength={30} pattern="[a-zA-Z0-9]{4,30}" value={account.username} onChange={(event) => setAccount((current) => ({ ...current, username: event.target.value }))} /></label><label className="field"><span>账号昵称</span><input minLength={2} maxLength={30} value={account.nickname} onChange={(event) => setAccount((current) => ({ ...current, nickname: event.target.value }))} /></label><label className="field"><span>初始密码</span><input autoComplete="new-password" minLength={8} maxLength={64} type="password" value={account.password} onChange={(event) => setAccount((current) => ({ ...current, password: event.target.value }))} /></label></> : <label className="field"><span>拒绝原因</span><textarea maxLength={255} minLength={2} placeholder="说明资质或资料需要修改的具体原因" rows={5} value={reason} onChange={(event) => setReason(event.target.value)} /></label>}<div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setReviewTarget(null)}>取消</button><button className={reviewMode === "reject" ? "button button--danger" : "button button--primary"} disabled={busy} type="button" onClick={() => { if (validateReview()) setConfirmReview(true); }}>{reviewMode === "approve" ? "提交通过" : "提交拒绝"}</button></div></div> : null}</Drawer>
      <ConfirmDialog open={confirmReview} title={reviewMode === "approve" ? "确认通过商家审核" : "确认拒绝商家申请"} description={reviewMode === "approve" ? `将通过“${reviewTarget?.name ?? ""}”并创建后台主账号，确认资料已核验完整。` : `将拒绝“${reviewTarget?.name ?? ""}”，申请方需要根据原因重新提交。`} confirmLabel={reviewMode === "approve" ? "确认通过" : "确认拒绝"} dangerous={reviewMode === "reject"} busy={busy} onClose={() => setConfirmReview(false)} onConfirm={() => void review()} />
    </section>
  );
}
