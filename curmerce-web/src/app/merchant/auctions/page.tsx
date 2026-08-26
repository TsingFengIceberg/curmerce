"use client";

import { BarChart3, Clock3, Copy, Eye, Gavel, Pencil, Plus, Search, Trophy } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { MerchantProductPicker } from "@/components/merchant-product-picker";
import { adminAuctionApi } from "@/lib/api/auction";
import { adminProductApi } from "@/lib/api/admin-product";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { ensureMerchantOwner } from "@/lib/auth/guards";
import { formatDateTime, formatMoney, toDateTimeMillis } from "@/lib/format";
import type { AuctionCreateInput, AuctionSession, ProductAdmin, ProductSkuAdmin } from "@/lib/types/api";

const PAGE_SIZE = 12;
const labels: Record<number, string> = { 0: "草稿", 10: "待开始", 20: "进行中", 30: "已结束", 40: "已取消", 50: "结算失败" };
type AuctionAction = "publish" | "cancel" | "end";
type AuctionForm = { name: string; productId: number; skuId: number; startingPriceYuan: string; minIncrementYuan: string; startTime: string; endTime: string };
type EditorState = { mode: "create" | "edit" | "copy"; id?: number } | null;

function toLocalInput(value?: string | number | null) {
  const millis = toDateTimeMillis(value);
  if (!Number.isFinite(millis)) return "";
  const date = new Date(millis);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function initialTimes(duration = 24 * 60 * 60 * 1_000) {
  const start = new Date(Date.now() + 60 * 60 * 1_000);
  start.setSeconds(0, 0);
  return { startTime: toLocalInput(start.getTime()), endTime: toLocalInput(start.getTime() + duration) };
}

function blankForm(): AuctionForm {
  return { name: "", productId: 0, skuId: 0, startingPriceYuan: "0.00", minIncrementYuan: "1.00", ...initialTimes() };
}

function sessionForm(session: AuctionSession, copying = false): AuctionForm {
  const duration = Math.max(60 * 60 * 1_000, toDateTimeMillis(session.endTime) - toDateTimeMillis(session.startTime));
  return {
    name: copying ? `${session.name} 副本` : session.name,
    productId: session.productId,
    skuId: session.skuId,
    startingPriceYuan: (session.startingPrice / 100).toFixed(2),
    minIncrementYuan: (session.minIncrement / 100).toFixed(2),
    ...(copying ? initialTimes(duration) : { startTime: toLocalInput(session.startTime), endTime: toLocalInput(session.endTime) }),
  };
}

function sellableSkus(product: ProductAdmin | undefined): ProductSkuAdmin[] {
  return product?.skus.filter((sku) => sku.id && sku.status === 0 && sku.stock > 0) ?? [];
}

function skuLabel(sku: ProductSkuAdmin) {
  const specs = sku.specificationValues?.map((item) => `${item.name}: ${item.value}`).join(" / ");
  return `${sku.code}${specs ? ` · ${specs}` : ""} · 库存 ${sku.stock} · ${formatMoney(sku.price)}`;
}

export default function MerchantAuctionsPage() {
  const router = useRouter();
  const [sessions, setSessions] = useState<AuctionSession[]>([]);
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState("");
  const [name, setName] = useState("");
  const [nameInput, setNameInput] = useState("");
  const [stats, setStats] = useState({ drafts: 0, running: 0 });
  const [editor, setEditor] = useState<EditorState>(null);
  const [form, setForm] = useState<AuctionForm>(blankForm);
  const [detail, setDetail] = useState<AuctionSession | null>(null);
  const [pending, setPending] = useState<{ session: AuctionSession; action: AuctionAction } | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void ensureMerchantOwner(router).then((allowed) => { if (allowed) void load(); });
  }, [router, pageNo, status, name]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [page, draftPage, runningPage] = await Promise.all([
        adminAuctionApi.page({ pageNo, pageSize: PAGE_SIZE, status: status ? Number(status) : undefined, name }),
        adminAuctionApi.page({ pageNo: 1, pageSize: 1, status: 0 }),
        adminAuctionApi.page({ pageNo: 1, pageSize: 1, status: 20 }),
      ]);
      setSessions(page.list ?? []);
      setTotal(page.total ?? 0);
      setStats({ drafts: draftPage.total ?? 0, running: runningPage.total ?? 0 });
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "拍卖场次加载失败");
    } finally {
      setLoading(false);
    }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNo(1);
    setName(nameInput.trim());
  }

  function openCreate() {
    setForm(blankForm());
    setEditor({ mode: "create" });
    setError(null);
  }

  async function openEdit(session: AuctionSession) {
    if (!await hydrateProduct(session.productId)) return;
    setForm(sessionForm(session));
    setEditor({ mode: "edit", id: session.id });
  }

  async function openCopy(session: AuctionSession) {
    if (!await hydrateProduct(session.productId)) return;
    setForm(sessionForm(session, true));
    setEditor({ mode: "copy" });
  }

  function mergeProducts(incoming: ProductAdmin[]) {
    setProducts((current) => Array.from(new Map([...current, ...incoming].map((product) => [product.id, product])).values()));
  }

  async function hydrateProduct(productId: number) {
    setError(null);
    if (products.some((product) => product.id === productId)) return true;
    try {
      mergeProducts([await adminProductApi.detailOwn(productId)]);
      return true;
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "拍卖关联商品加载失败");
      return false;
    }
  }

  function selectProduct(product: ProductAdmin) {
    mergeProducts([product]);
    const sku = sellableSkus(product)[0];
    setForm((current) => ({ ...current, productId: product.id, skuId: sku?.id ?? 0, startingPriceYuan: sku ? (sku.price / 100).toFixed(2) : current.startingPriceYuan }));
  }

  function inputPayload(): AuctionCreateInput | null {
    const start = new Date(form.startTime).getTime();
    const end = new Date(form.endTime).getTime();
    const startingPrice = Math.round(Number(form.startingPriceYuan) * 100);
    const minIncrement = Math.round(Number(form.minIncrementYuan) * 100);
    const sku = sellableSkus(products.find((product) => product.id === form.productId)).find((item) => item.id === form.skuId);
    if (!form.name.trim() || !sku) {
      setError("请填写拍卖名称并选择有库存的商品 SKU");
      return null;
    }
    if (!Number.isFinite(startingPrice) || startingPrice < 0 || !Number.isFinite(minIncrement) || minIncrement < 1) {
      setError("起拍价不能小于 0 元，最低加价至少为 0.01 元");
      return null;
    }
    if (!Number.isFinite(start) || start <= Date.now() || !Number.isFinite(end) || end <= start) {
      setError("开始时间必须晚于现在，结束时间必须晚于开始时间");
      return null;
    }
    return { name: form.name.trim(), productId: form.productId, skuId: form.skuId, startingPrice, minIncrement, startTime: form.startTime, endTime: form.endTime };
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = inputPayload();
    if (!input || !editor) return;
    setBusy(true);
    setError(null);
    try {
      if (editor.mode === "edit" && editor.id) await adminAuctionApi.update(editor.id, input);
      else await adminAuctionApi.create(input);
      setMessage(editor.mode === "edit" ? "拍卖草稿已更新" : editor.mode === "copy" ? "拍卖副本已创建为草稿" : "拍卖草稿已创建");
      setEditor(null);
      await load();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "拍卖保存失败");
    } finally {
      setBusy(false);
    }
  }

  async function transition() {
    if (!pending) return;
    setBusy(true);
    setError(null);
    try {
      await adminAuctionApi[pending.action](pending.session.id);
      setMessage(pending.action === "publish" ? "拍卖已发布" : pending.action === "cancel" ? "拍卖已取消" : "拍卖已完成结算");
      setPending(null);
      await load();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "拍卖状态更新失败");
    } finally {
      setBusy(false);
    }
  }

  const selectedProduct = products.find((product) => product.id === form.productId);
  const availableSkus = sellableSkus(selectedProduct);
  const pageBids = sessions.reduce((sum, session) => sum + (session.bidCount ?? 0), 0);
  const actionMeta = pending?.action === "publish" ? { title: "发布拍卖", description: "发布后场次将按计划开放竞价，草稿内容不能再编辑。", label: "确认发布", dangerous: false }
    : pending?.action === "end" ? { title: "结束并结算拍卖", description: "系统将确定最高出价和中标用户；仅到达结束时间后可执行。", label: "确认结算", dangerous: false }
      : { title: "取消拍卖", description: "取消后场次不会再开放竞价，当前操作不可撤销。", label: "确认取消", dangerous: true };

  return (
    <section className="content-section admin-page merchant-activity-page">
      <div className="section-heading"><div><p className="eyebrow">MERCHANT · AUCTION</p><h1>拍卖管理</h1><p>管理拍卖草稿、竞价排期和场次结算。</p></div><button className="button button--primary button--icon-label" type="button" onClick={openCreate}><Plus aria-hidden="true" size={17} />创建拍卖</button></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="metric-grid activity-metrics">
        <div className="metric-tile"><div className="metric-tile__top"><span>全部场次</span><span className="metric-tile__icon"><BarChart3 aria-hidden="true" /></span></div><strong>{total}</strong><span>当前筛选结果</span><small>支持按名称与状态查询</small></div>
        <div className="metric-tile"><div className="metric-tile__top"><span>待完善</span><span className="metric-tile__icon"><Clock3 aria-hidden="true" /></span></div><strong>{stats.drafts}</strong><span>草稿场次</span><small>可编辑或复制后再发布</small></div>
        <div className="metric-tile"><div className="metric-tile__top"><span>竞价中</span><span className="metric-tile__icon"><Gavel aria-hidden="true" /></span></div><strong>{stats.running}</strong><span>进行中场次</span><small>持续接收有效出价</small></div>
        <div className="metric-tile"><div className="metric-tile__top"><span>本页热度</span><span className="metric-tile__icon"><Trophy aria-hidden="true" /></span></div><strong>{pageBids}</strong><span>累计出价次数</span><small>基于当前页场次汇总</small></div>
      </div>
      <div className="workspace-section merchant-activity-panel">
        <div className="activity-toolbar"><select aria-label="拍卖状态" value={status} onChange={(event) => { setStatus(event.target.value); setPageNo(1); }}><option value="">全部状态</option>{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><form className="order-search" onSubmit={search}><Search aria-hidden="true" size={16} /><input aria-label="拍卖名称" placeholder="搜索拍卖名称" value={nameInput} onChange={(event) => setNameInput(event.target.value)} /><button type="submit">查询</button></form><span>共 {total} 个场次</span></div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !sessions.length ? <EmptyState icon={<Gavel aria-hidden="true" size={23} />} title="没有符合条件的拍卖" description="调整筛选条件，或从有库存的商品创建拍卖。" actionLabel="创建拍卖" onAction={openCreate} /> : null}
        {!loading && sessions.length ? <div className="activity-table activity-table--auction"><div className="activity-table__head"><span>拍卖商品</span><span>排期</span><span>竞价数据</span><span>状态</span><span>操作</span></div>{sessions.map((session) => <article className="activity-table__row" key={session.id}><div className="activity-table__product">{assetUrl(session.productImageUrl) ? <img alt={session.productName || session.name} src={assetUrl(session.productImageUrl) ?? ""} /> : <span className="listing-table__placeholder">C</span>}<span><strong>{session.name}</strong><small>{session.productName || `商品 ${session.productId}`} · {session.skuLabel || `SKU ${session.skuId}`}</small></span></div><div className="activity-table__schedule"><strong>{formatDateTime(session.startTime)}</strong><small>至 {formatDateTime(session.endTime)}</small></div><div className="activity-table__metric"><strong>{session.currentAmount == null ? `起拍 ${formatMoney(session.startingPrice)}` : `当前 ${formatMoney(session.currentAmount)}`}</strong><small>{session.bidCount ?? 0} 次出价 · 加价 {formatMoney(session.minIncrement)}</small></div><span className={`tag activity-status activity-status--${session.status}`}>{labels[session.status] ?? session.status}</span><div className="listing-table__actions"><button aria-label={`查看 ${session.name}`} className="icon-button" title="查看详情" type="button" onClick={() => setDetail(session)}><Eye aria-hidden="true" size={16} /></button>{session.status === 0 ? <button aria-label={`编辑 ${session.name}`} className="icon-button" title="编辑草稿" type="button" onClick={() => openEdit(session)}><Pencil aria-hidden="true" size={16} /></button> : null}<button aria-label={`复制 ${session.name}`} className="icon-button" title="复制为草稿" type="button" onClick={() => openCopy(session)}><Copy aria-hidden="true" size={16} /></button>{session.status === 0 ? <button className="text-button" type="button" onClick={() => setPending({ session, action: "publish" })}>发布</button> : null}{session.status === 0 || session.status === 10 ? <button className="text-button text-button--danger" type="button" onClick={() => setPending({ session, action: "cancel" })}>取消</button> : null}{session.status === 20 && Date.now() >= toDateTimeMillis(session.endTime) ? <button className="text-button" type="button" onClick={() => setPending({ session, action: "end" })}>结算</button> : null}</div></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>

      <Drawer open={Boolean(editor)} title={editor?.mode === "edit" ? "编辑拍卖草稿" : editor?.mode === "copy" ? "复制拍卖场次" : "创建拍卖"} description="从当前店铺已审核上架且有库存的 SKU 中选择拍卖商品。" busy={busy} onClose={() => setEditor(null)}>
        <form className="drawer-form activity-editor" onSubmit={save}><label className="field"><span>拍卖名称</span><input required value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} /></label><div className="field"><span>商品</span><MerchantProductPicker enabled={Boolean(editor)} selected={selectedProduct} onSelect={selectProduct} /></div><label className="field"><span>SKU</span><select required disabled={!selectedProduct} value={form.skuId || ""} onChange={(event) => setForm((current) => ({ ...current, skuId: Number(event.target.value) }))}><option value="">请选择 SKU</option>{availableSkus.map((sku) => <option key={sku.id} value={sku.id}>{skuLabel(sku)}</option>)}</select></label><div className="admin-form-grid"><label className="field"><span>起拍价（元）</span><input min="0" required step="0.01" type="number" value={form.startingPriceYuan} onChange={(event) => setForm((current) => ({ ...current, startingPriceYuan: event.target.value }))} /></label><label className="field"><span>最低加价（元）</span><input min="0.01" required step="0.01" type="number" value={form.minIncrementYuan} onChange={(event) => setForm((current) => ({ ...current, minIncrementYuan: event.target.value }))} /></label><label className="field"><span>开始时间</span><input required type="datetime-local" value={form.startTime} onChange={(event) => setForm((current) => ({ ...current, startTime: event.target.value }))} /></label><label className="field"><span>结束时间</span><input required type="datetime-local" value={form.endTime} onChange={(event) => setForm((current) => ({ ...current, endTime: event.target.value }))} /></label></div><div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setEditor(null)}>取消</button><button className="button button--primary" disabled={busy || !form.productId || !form.skuId} type="submit">{busy ? "保存中…" : editor?.mode === "edit" ? "保存修改" : "保存草稿"}</button></div></form>
      </Drawer>

      <Drawer open={Boolean(detail)} title="拍卖详情" description={detail ? detail.name : ""} onClose={() => setDetail(null)}>{detail ? <div className="drawer-form"><div className="activity-detail-product">{assetUrl(detail.productImageUrl) ? <img alt={detail.productName || detail.name} src={assetUrl(detail.productImageUrl) ?? ""} /> : <span className="listing-table__placeholder">C</span>}<div><strong>{detail.productName || `商品 ${detail.productId}`}</strong><small>{detail.skuLabel || `SKU ${detail.skuId}`}</small><span>商品原价 {formatMoney(detail.originalPrice)}</span></div></div><div className="detail-rows"><div><span>场次状态</span><strong>{labels[detail.status] ?? detail.status}</strong></div><div><span>拍卖排期</span><strong>{formatDateTime(detail.startTime)} 至 {formatDateTime(detail.endTime)}</strong></div><div><span>价格规则</span><strong>起拍 {formatMoney(detail.startingPrice)} · 最低加价 {formatMoney(detail.minIncrement)}</strong></div><div><span>竞价表现</span><strong>{detail.bidCount ?? 0} 次出价 · {detail.currentAmount == null ? "暂无出价" : `当前 ${formatMoney(detail.currentAmount)}`}</strong></div>{detail.winnerUserId ? <div><span>结算结果</span><strong>中标用户 {detail.winnerUserId}</strong></div> : null}{detail.settlementFailureReason ? <div><span>结算异常</span><strong>{detail.settlementFailureReason}</strong></div> : null}</div></div> : null}</Drawer>
      <ConfirmDialog open={Boolean(pending)} title={actionMeta.title} description={actionMeta.description} confirmLabel={actionMeta.label} dangerous={actionMeta.dangerous} busy={busy} onClose={() => { if (!busy) setPending(null); }} onConfirm={() => void transition()} />
    </section>
  );
}
