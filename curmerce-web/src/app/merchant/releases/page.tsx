"use client";

import { BarChart3, CalendarClock, Copy, Eye, Pencil, Plus, Rocket, Search, ShoppingBasket } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { MerchantProductPicker } from "@/components/merchant-product-picker";
import { adminProductApi } from "@/lib/api/admin-product";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { adminReleaseApi } from "@/lib/api/release";
import { ensureMerchantOwner } from "@/lib/auth/guards";
import { formatDateTime, formatMoney, toDateTimeMillis } from "@/lib/format";
import type { ProductAdmin, ProductSkuAdmin, ReleaseCampaign, ReleaseCreateInput } from "@/lib/types/api";

const PAGE_SIZE = 12;
const labels: Record<number, string> = { 0: "草稿", 10: "待开始", 20: "进行中", 30: "已结束", 40: "已取消" };
type ReleaseAction = "publish" | "cancel" | "finish";
type ReleaseFormItem = { productId: number; skuId: number; priceYuan: string; stock: number };
type ReleaseForm = { name: string; startTime: string; endTime: string; perUserLimit: number; items: ReleaseFormItem[] };
type EditorState = { mode: "create" | "edit" | "copy"; id?: number } | null;

const newItem = (): ReleaseFormItem => ({ productId: 0, skuId: 0, priceYuan: "0.00", stock: 1 });

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

function blankForm(): ReleaseForm {
  return { name: "", ...initialTimes(), perUserLimit: 1, items: [newItem()] };
}

function sellableSkus(product: ProductAdmin | undefined): ProductSkuAdmin[] {
  return product?.skus.filter((sku) => sku.id && sku.status === 0 && sku.stock > 0) ?? [];
}

function skuLabel(sku: ProductSkuAdmin) {
  const specs = sku.specificationValues?.map((item) => `${item.name}: ${item.value}`).join(" / ");
  return `${sku.code}${specs ? ` · ${specs}` : ""} · 库存 ${sku.stock} · ${formatMoney(sku.price)}`;
}

function campaignForm(campaign: ReleaseCampaign, copying = false): ReleaseForm {
  const duration = Math.max(60 * 60 * 1_000, toDateTimeMillis(campaign.endTime) - toDateTimeMillis(campaign.startTime));
  const times = copying ? initialTimes(duration) : { startTime: toLocalInput(campaign.startTime), endTime: toLocalInput(campaign.endTime) };
  return {
    name: copying ? `${campaign.name} 副本` : campaign.name,
    ...times,
    perUserLimit: campaign.perUserLimit,
    items: campaign.items.map((item) => ({ productId: item.productId, skuId: item.skuId, priceYuan: (item.campaignPrice / 100).toFixed(2), stock: Math.max(1, item.stock) })),
  };
}

export default function MerchantReleasesPage() {
  const router = useRouter();
  const [campaigns, setCampaigns] = useState<ReleaseCampaign[]>([]);
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState("");
  const [name, setName] = useState("");
  const [nameInput, setNameInput] = useState("");
  const [stats, setStats] = useState({ drafts: 0, running: 0 });
  const [editor, setEditor] = useState<EditorState>(null);
  const [form, setForm] = useState<ReleaseForm>(blankForm);
  const [detail, setDetail] = useState<ReleaseCampaign | null>(null);
  const [pending, setPending] = useState<{ campaign: ReleaseCampaign; action: ReleaseAction } | null>(null);
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
        adminReleaseApi.page({ pageNo, pageSize: PAGE_SIZE, status: status ? Number(status) : undefined, name }),
        adminReleaseApi.page({ pageNo: 1, pageSize: 1, status: 0 }),
        adminReleaseApi.page({ pageNo: 1, pageSize: 1, status: 20 }),
      ]);
      setCampaigns(page.list ?? []);
      setTotal(page.total ?? 0);
      setStats({ drafts: draftPage.total ?? 0, running: runningPage.total ?? 0 });
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "限时发售活动加载失败");
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

  async function openEdit(campaign: ReleaseCampaign) {
    if (!await hydrateProducts(campaign.items.map((item) => item.productId))) return;
    setForm(campaignForm(campaign));
    setEditor({ mode: "edit", id: campaign.id });
  }

  async function openCopy(campaign: ReleaseCampaign) {
    if (!await hydrateProducts(campaign.items.map((item) => item.productId))) return;
    setForm(campaignForm(campaign, true));
    setEditor({ mode: "copy" });
  }

  function mergeProducts(incoming: ProductAdmin[]) {
    setProducts((current) => Array.from(new Map([...current, ...incoming].map((product) => [product.id, product])).values()));
  }

  async function hydrateProducts(productIds: number[]) {
    const missing = [...new Set(productIds)].filter((id) => !products.some((product) => product.id === id));
    setError(null);
    if (!missing.length) return true;
    try {
      mergeProducts(await Promise.all(missing.map((id) => adminProductApi.detailOwn(id))));
      return true;
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "活动关联商品加载失败");
      return false;
    }
  }

  function updateItem(index: number, changes: Partial<ReleaseFormItem>) {
    setForm((current) => ({ ...current, items: current.items.map((item, itemIndex) => itemIndex === index ? { ...item, ...changes } : item) }));
  }

  function usedSkuIds(exceptIndex: number) {
    return new Set(form.items.filter((_, index) => index !== exceptIndex).map((item) => item.skuId).filter(Boolean));
  }

  function availableSkus(index: number, productId: number) {
    const used = usedSkuIds(index);
    return sellableSkus(products.find((product) => product.id === productId)).filter((sku) => sku.id && !used.has(sku.id));
  }

  function selectProduct(index: number, product: ProductAdmin) {
    mergeProducts([product]);
    const used = usedSkuIds(index);
    const sku = sellableSkus(product).find((item) => item.id && !used.has(item.id));
    updateItem(index, { productId: product.id, skuId: sku?.id ?? 0, priceYuan: sku ? (sku.price / 100).toFixed(2) : "0.00", stock: sku ? Math.min(form.items[index].stock, sku.stock) : 1 });
  }

  function selectedSku(item: ReleaseFormItem) {
    return sellableSkus(products.find((product) => product.id === item.productId)).find((sku) => sku.id === item.skuId);
  }

  function inputPayload(): ReleaseCreateInput | null {
    const startTime = new Date(form.startTime).getTime();
    const endTime = new Date(form.endTime).getTime();
    const items = form.items.map((item) => ({ ...item, campaignPrice: Math.round(Number(item.priceYuan) * 100) }));
    if (!form.name.trim() || !Number.isFinite(startTime) || startTime <= Date.now() || !Number.isFinite(endTime) || endTime <= startTime) {
      setError("请填写活动名称，并确保开始时间晚于现在、结束时间晚于开始时间");
      return null;
    }
    if (form.perUserLimit < 1 || items.some((item) => { const sku = selectedSku(item); return !sku || !Number.isFinite(item.campaignPrice) || item.campaignPrice < 0 || item.stock < 1 || item.stock > sku.stock; })) {
      setError("请完整选择商品与 SKU，并确保价格有效、活动库存不超过可用库存");
      return null;
    }
    if (new Set(items.map((item) => item.skuId)).size !== items.length) {
      setError("同一活动不能重复选择相同 SKU");
      return null;
    }
    return { name: form.name.trim(), startTime: form.startTime, endTime: form.endTime, perUserLimit: form.perUserLimit, items: items.map(({ productId, skuId, campaignPrice, stock }) => ({ productId, skuId, campaignPrice, stock })) };
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = inputPayload();
    if (!input || !editor) return;
    setBusy(true);
    setError(null);
    try {
      if (editor.mode === "edit" && editor.id) await adminReleaseApi.update(editor.id, input);
      else await adminReleaseApi.create(input);
      setMessage(editor.mode === "edit" ? "限时发售草稿已更新" : editor.mode === "copy" ? "活动副本已创建为草稿" : "限时发售草稿已创建");
      setEditor(null);
      await load();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "活动保存失败");
    } finally {
      setBusy(false);
    }
  }

  async function transition() {
    if (!pending) return;
    setBusy(true);
    setError(null);
    try {
      await adminReleaseApi[pending.action](pending.campaign.id);
      setMessage(pending.action === "publish" ? "活动已发布" : pending.action === "cancel" ? "活动已取消" : "活动已结束");
      setPending(null);
      await load();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "活动状态更新失败");
    } finally {
      setBusy(false);
    }
  }

  const pageSold = campaigns.reduce((sum, campaign) => sum + campaign.items.reduce((itemSum, item) => itemSum + item.soldCount, 0), 0);
  const actionMeta = pending?.action === "publish" ? { title: "发布限时发售", description: "发布后活动将按设定时间开放购买，草稿内容不能再编辑。", label: "确认发布", dangerous: false }
    : pending?.action === "finish" ? { title: "提前结束活动", description: "结束后买家将不能继续购买，当前操作不可撤销。", label: "确认结束", dangerous: true }
      : { title: "取消活动", description: "取消后活动不会再向买家开放，当前操作不可撤销。", label: "确认取消", dangerous: true };

  return (
    <section className="content-section admin-page merchant-activity-page">
      <div className="section-heading"><div><p className="eyebrow">MERCHANT · LIMITED RELEASE</p><h1>限时发售</h1><p>管理活动草稿、排期、库存和销售进度。</p></div><button className="button button--primary button--icon-label" type="button" onClick={openCreate}><Plus aria-hidden="true" size={17} />创建活动</button></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="metric-grid activity-metrics">
        <div className="metric-tile"><div className="metric-tile__top"><span>全部活动</span><span className="metric-tile__icon"><BarChart3 aria-hidden="true" /></span></div><strong>{total}</strong><span>当前筛选结果</span><small>支持按名称与状态查询</small></div>
        <div className="metric-tile"><div className="metric-tile__top"><span>待完善</span><span className="metric-tile__icon"><CalendarClock aria-hidden="true" /></span></div><strong>{stats.drafts}</strong><span>草稿活动</span><small>可继续编辑或复制</small></div>
        <div className="metric-tile"><div className="metric-tile__top"><span>销售中</span><span className="metric-tile__icon"><Rocket aria-hidden="true" /></span></div><strong>{stats.running}</strong><span>进行中活动</span><small>可查看实时剩余库存</small></div>
        <div className="metric-tile"><div className="metric-tile__top"><span>本页成交</span><span className="metric-tile__icon"><ShoppingBasket aria-hidden="true" /></span></div><strong>{pageSold}</strong><span>已售件数</span><small>基于当前页活动汇总</small></div>
      </div>
      <div className="workspace-section merchant-activity-panel">
        <div className="activity-toolbar"><select aria-label="活动状态" value={status} onChange={(event) => { setStatus(event.target.value); setPageNo(1); }}><option value="">全部状态</option>{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><form className="order-search" onSubmit={search}><Search aria-hidden="true" size={16} /><input aria-label="活动名称" placeholder="搜索活动名称" value={nameInput} onChange={(event) => setNameInput(event.target.value)} /><button type="submit">查询</button></form><span>共 {total} 个活动</span></div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !campaigns.length ? <EmptyState icon={<CalendarClock aria-hidden="true" size={23} />} title="没有符合条件的活动" description="调整筛选条件，或创建一场新的限时发售。" actionLabel="创建活动" onAction={openCreate} /> : null}
        {!loading && campaigns.length ? <div className="activity-table activity-table--release"><div className="activity-table__head"><span>活动</span><span>排期</span><span>销售数据</span><span>状态</span><span>操作</span></div>{campaigns.map((campaign) => { const stock = campaign.items.reduce((sum, item) => sum + item.stock, 0); const sold = campaign.items.reduce((sum, item) => sum + item.soldCount, 0); return <article className="activity-table__row" key={campaign.id}><div className="activity-table__identity"><strong>{campaign.name}</strong><small>{campaign.items.length} 个 SKU · 每人限购 {campaign.perUserLimit} 件</small></div><div className="activity-table__schedule"><strong>{formatDateTime(campaign.startTime)}</strong><small>至 {formatDateTime(campaign.endTime)}</small></div><div className="activity-table__metric"><strong>已售 {sold}</strong><small>剩余 {stock} 件</small></div><span className={`tag activity-status activity-status--${campaign.status}`}>{labels[campaign.status] ?? campaign.status}</span><div className="listing-table__actions"><button aria-label={`查看 ${campaign.name}`} className="icon-button" title="查看详情" type="button" onClick={() => setDetail(campaign)}><Eye aria-hidden="true" size={16} /></button>{campaign.status === 0 ? <button aria-label={`编辑 ${campaign.name}`} className="icon-button" title="编辑草稿" type="button" onClick={() => openEdit(campaign)}><Pencil aria-hidden="true" size={16} /></button> : null}<button aria-label={`复制 ${campaign.name}`} className="icon-button" title="复制为草稿" type="button" onClick={() => openCopy(campaign)}><Copy aria-hidden="true" size={16} /></button>{campaign.status === 0 ? <button className="text-button" type="button" onClick={() => setPending({ campaign, action: "publish" })}>发布</button> : null}{campaign.status === 0 || campaign.status === 10 ? <button className="text-button text-button--danger" type="button" onClick={() => setPending({ campaign, action: "cancel" })}>取消</button> : null}{campaign.status === 20 ? <button className="text-button text-button--danger" type="button" onClick={() => setPending({ campaign, action: "finish" })}>结束</button> : null}</div></article>; })}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>

      <Drawer open={Boolean(editor)} title={editor?.mode === "edit" ? "编辑活动草稿" : editor?.mode === "copy" ? "复制限时发售" : "创建限时发售"} description="金额按人民币元填写，活动库存不能超过商品 SKU 可用库存。" busy={busy} onClose={() => setEditor(null)}>
        <form className="drawer-form activity-editor" onSubmit={save}>
          <div className="admin-form-grid"><label className="field"><span>活动名称</span><input required value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} /></label><label className="field"><span>每人限购</span><input min="1" required type="number" value={form.perUserLimit} onChange={(event) => setForm((current) => ({ ...current, perUserLimit: Number(event.target.value) }))} /></label><label className="field"><span>开始时间</span><input required type="datetime-local" value={form.startTime} onChange={(event) => setForm((current) => ({ ...current, startTime: event.target.value }))} /></label><label className="field"><span>结束时间</span><input required type="datetime-local" value={form.endTime} onChange={(event) => setForm((current) => ({ ...current, endTime: event.target.value }))} /></label></div>
          <div className="event-form-items">{form.items.map((item, index) => { const sku = selectedSku(item); const skuOptions = availableSkus(index, item.productId); return <div className="event-form-item" key={index}><div className="activity-editor__item-heading"><strong>活动 SKU {index + 1}</strong>{form.items.length > 1 ? <button className="text-button text-button--danger" type="button" onClick={() => setForm((current) => ({ ...current, items: current.items.filter((_, itemIndex) => itemIndex !== index) }))}>移除</button> : null}</div><div className="field activity-editor__product-picker"><span>商品</span><MerchantProductPicker enabled={Boolean(editor)} selected={products.find((product) => product.id === item.productId)} excludedSkuIds={[...usedSkuIds(index)]} onSelect={(product) => selectProduct(index, product)} /></div><label className="field"><span>SKU</span><select required disabled={!item.productId} value={item.skuId || ""} onChange={(event) => { const skuId = Number(event.target.value); const nextSku = sellableSkus(products.find((product) => product.id === item.productId)).find((entry) => entry.id === skuId); updateItem(index, { skuId, priceYuan: nextSku ? (nextSku.price / 100).toFixed(2) : item.priceYuan, stock: nextSku ? Math.min(item.stock, nextSku.stock) : item.stock }); }}><option value="">请选择 SKU</option>{skuOptions.map((option) => <option key={option.id} value={option.id}>{skuLabel(option)}</option>)}</select></label><label className="field"><span>活动价（元）</span><input min="0" required step="0.01" type="number" value={item.priceYuan} onChange={(event) => updateItem(index, { priceYuan: event.target.value })} /></label><label className="field"><span>活动库存{sku ? `（最多 ${sku.stock}）` : ""}</span><input min="1" max={sku?.stock} required type="number" value={item.stock} onChange={(event) => updateItem(index, { stock: Number(event.target.value) })} /></label></div>; })}</div>
          <div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setForm((current) => ({ ...current, items: [...current.items, newItem()] }))}>添加 SKU</button><button className="button button--primary" disabled={busy || form.items.some((item) => !item.productId || !item.skuId)} type="submit">{busy ? "保存中…" : editor?.mode === "edit" ? "保存修改" : "保存草稿"}</button></div>
        </form>
      </Drawer>

      <Drawer open={Boolean(detail)} title="活动详情" description={detail ? detail.name : ""} onClose={() => setDetail(null)}>{detail ? <div className="drawer-form"><div className="detail-rows"><div><span>活动状态</span><strong>{labels[detail.status] ?? detail.status}</strong></div><div><span>活动排期</span><strong>{formatDateTime(detail.startTime)} 至 {formatDateTime(detail.endTime)}</strong></div><div><span>每人限购</span><strong>{detail.perUserLimit} 件</strong></div><div><span>活动 SKU</span><strong>{detail.items.length} 个</strong></div></div><div className="drawer-order-items"><h3>SKU 销售数据</h3>{detail.items.map((item) => <div key={item.id}>{assetUrl(item.productImageUrl) ? <img alt={item.productName || "活动商品"} src={assetUrl(item.productImageUrl) ?? ""} /> : <span className="listing-table__placeholder">C</span>}<span><strong>{item.productName || `商品 ${item.productId}`}</strong><small>{item.skuLabel || `SKU ${item.skuId}`} · 已售 {item.soldCount} · 剩余 {item.stock}</small></span><b>{formatMoney(item.campaignPrice)}</b></div>)}</div></div> : null}</Drawer>
      <ConfirmDialog open={Boolean(pending)} title={actionMeta.title} description={actionMeta.description} confirmLabel={actionMeta.label} dangerous={actionMeta.dangerous} busy={busy} onClose={() => { if (!busy) setPending(null); }} onConfirm={() => void transition()} />
    </section>
  );
}
