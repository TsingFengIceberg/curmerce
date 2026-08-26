"use client";

import { CheckCircle2, ClipboardCheck, Eye, History, Search, XCircle } from "lucide-react";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { ProductOperationHistory } from "@/components/product-operation-history";
import { notifyWorkspaceBadgesChanged } from "@/components/workspace-shell";
import { adminProductApi } from "@/lib/api/admin-product";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { ProductAdmin } from "@/lib/types/api";
import { positiveInt, useUrlQuery } from "@/hooks/use-url-query";
import { notifyFeedback } from "@/components/feedback-center";
import { TableDensityControl, useTableDensity } from "@/components/table-view-controls";

const PAGE_SIZE = 15;
const auditLabels: Record<number, string> = { 0: "草稿", 1: "待审核", 2: "审核通过", 3: "已驳回" };
type ReviewMode = "approve" | "reject";

export default function AdminProductReviewPage() {
  const router = useRouter();
  const { searchParams, update } = useUrlQuery();
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [total, setTotal] = useState(0);
  const pageNo = positiveInt(searchParams.get("page"));
  const auditStatus = searchParams.get("auditStatus") ?? "1";
  const keyword = searchParams.get("name") ?? "";
  const dateFrom = searchParams.get("dateFrom") ?? "";
  const dateTo = searchParams.get("dateTo") ?? "";
  const sort = searchParams.get("sort") ?? "newest";
  const { density, setDensity } = useTableDensity("product-review");
  const [keywordInput, setKeywordInput] = useState(keyword);
  const [selected, setSelected] = useState<Map<number, ProductAdmin>>(new Map());
  const [confirmBatch, setConfirmBatch] = useState(false);
  const [detail, setDetail] = useState<ProductAdmin | null>(null);
  const [reviewTarget, setReviewTarget] = useState<ProductAdmin | null>(null);
  const [historyTarget, setHistoryTarget] = useState<ProductAdmin | null>(null);
  const [reviewMode, setReviewMode] = useState<ReviewMode>("approve");
  const [rejectReason, setRejectReason] = useState("");
  const [confirmReview, setConfirmReview] = useState(false);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAdminAccessToken()) { router.replace("/merchant/login"); return; }
    void load();
  }, [router, pageNo, auditStatus, keyword, dateFrom, dateTo, sort]);

  useEffect(() => setKeywordInput(keyword), [keyword]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await adminProductApi.reviewPage({ pageNo, pageSize: PAGE_SIZE, auditStatus: auditStatus ? Number(auditStatus) : undefined, name: keyword, dateFrom: dateFrom || undefined, dateTo: dateTo || undefined });
      setProducts([...(page.list ?? [])].sort((left, right) => sort === "oldest" ? left.id - right.id : right.id - left.id));
      setTotal(page.total ?? 0);
    } catch (cause) {
      handle(cause, "商品审核列表加载失败");
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
    update({ page: 1, name: keywordInput.trim() });
  }

  function toggleSelected(product: ProductAdmin) {
    setSelected((current) => {
      const next = new Map(current);
      if (next.has(product.id)) next.delete(product.id); else next.set(product.id, product);
      return next;
    });
  }

  function toggleCurrentPage() {
    const pending = products.filter((product) => product.auditStatus === 1);
    const allSelected = pending.length > 0 && pending.every((product) => selected.has(product.id));
    setSelected((current) => {
      const next = new Map(current);
      pending.forEach((product) => { if (allSelected) next.delete(product.id); else next.set(product.id, product); });
      return next;
    });
  }

  async function approveBatch() {
    const targets = Array.from(selected.values()).filter((product) => product.auditStatus === 1);
    setConfirmBatch(false);
    setBusy(true);
    const results = await Promise.allSettled(targets.map((product) => adminProductApi.approve(product.id)));
    const failures = results.filter((result) => result.status === "rejected").length;
    const succeeded = targets.length - failures;
    if (failures) notifyFeedback({ tone: "error", title: `批量审核完成：${succeeded} 件通过，${failures} 件失败`, description: "失败商品仍保留在选择中，可刷新后重试。", duration: 0 });
    else notifyFeedback({ tone: "success", title: `${succeeded} 件商品已审核通过` });
    setSelected((current) => new Map(Array.from(current).filter(([id]) => results[targets.findIndex((product) => product.id === id)]?.status === "rejected")));
    notifyWorkspaceBadgesChanged();
    await load();
    setBusy(false);
  }

  async function openDetail(id: number) {
    setDetailLoading(true);
    setError(null);
    try {
      setDetail(await adminProductApi.reviewDetail(id));
    } catch (cause) {
      handle(cause, "商品审核详情加载失败");
    } finally {
      setDetailLoading(false);
    }
  }

  async function openReview(product: ProductAdmin, mode: ReviewMode) {
    setDetail(null);
    setReviewMode(mode);
    setRejectReason("");
    setError(null);
    setDetailLoading(true);
    try {
      setReviewTarget(await adminProductApi.reviewDetail(product.id));
    } catch (cause) {
      handle(cause, "商品审核详情加载失败");
    } finally {
      setDetailLoading(false);
    }
  }

  async function review() {
    if (!reviewTarget) return;
    if (reviewMode === "reject" && !rejectReason.trim()) {
      setError("驳回商品时必须填写明确原因");
      return;
    }
    setConfirmReview(false);
    setBusy(true);
    setError(null);
    try {
      if (reviewMode === "approve") await adminProductApi.approve(reviewTarget.id);
      else await adminProductApi.reject(reviewTarget.id, rejectReason);
      setMessage(reviewMode === "approve" ? "商品审核已通过" : "商品已驳回并退回商家修改");
      notifyWorkspaceBadgesChanged();
      setReviewTarget(null);
      await load();
    } catch (cause) {
      handle(cause, "商品审核操作失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="content-section admin-page product-review-page">
      <div className="section-heading"><div><p className="eyebrow">ADMIN · PRODUCT REVIEW</p><h1>商品审核</h1><p>核验商品资料、图片、分类和 SKU 后给出审核结论。</p></div></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="workspace-section admin-data-panel">
        <div className="admin-data-toolbar"><select aria-label="商品审核状态" value={auditStatus} onChange={(event) => update({ auditStatus: event.target.value, page: 1 })}><option value="">全部状态</option><option value="1">待审核</option><option value="2">已通过</option><option value="3">已驳回</option><option value="0">草稿</option></select><form className="order-search" onSubmit={search}><Search aria-hidden="true" size={16} /><input aria-label="商品名称" placeholder="搜索商品名称" value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} /><button type="submit">查询</button></form><select aria-label="当前页排序" value={sort} onChange={(event) => update({ sort: event.target.value })}><option value="newest">最新提交</option><option value="oldest">最早提交</option></select><TableDensityControl density={density} onChange={setDensity} />{selected.size ? <button className="button button--primary button--small" disabled={busy} type="button" onClick={() => setConfirmBatch(true)}>批量通过（{selected.size}）</button> : null}<span>共 {total} 件</span></div>
        <div className="date-range-toolbar"><label><span>开始日期</span><input type="date" value={dateFrom} onChange={(event) => update({ dateFrom: event.target.value, page: 1 })} /></label><label><span>结束日期</span><input min={dateFrom} type="date" value={dateTo} onChange={(event) => update({ dateTo: event.target.value, page: 1 })} /></label>{dateFrom || dateTo ? <button className="text-button" type="button" onClick={() => update({ dateFrom: null, dateTo: null, page: 1 })}>清除日期</button> : null}{selected.size ? <span>已跨页选择 {selected.size} 件待审核商品</span> : null}</div>
        {loading || detailLoading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !products.length ? <EmptyState icon={<ClipboardCheck aria-hidden="true" size={23} />} title="没有符合条件的商品" description="调整审核状态或搜索条件后重新查询。" /> : null}
        {!loading && products.length ? <div className="admin-product-review-table admin-product-review-table--selectable"><div className="admin-product-review-table__head"><span><input aria-label="选择当前页待审核商品" checked={products.some((product) => product.auditStatus === 1) && products.filter((product) => product.auditStatus === 1).every((product) => selected.has(product.id))} type="checkbox" onChange={toggleCurrentPage} /></span><span>商品</span><span>商家 / 店铺</span><span>分类</span><span>提交时间</span><span>审核状态</span><span>操作</span></div>{products.map((product) => <article className="admin-product-review-table__row" key={product.id}><span><input aria-label={`选择商品 ${product.name}`} checked={selected.has(product.id)} disabled={product.auditStatus !== 1} type="checkbox" onChange={() => toggleSelected(product)} /></span><div className="admin-product-review-table__product"><MediaImage alt={product.name} fallback={<span className="listing-table__placeholder">C</span>} src={assetUrl(product.mainImageUrl)} /><div><strong>{product.name}</strong><small>{product.code}</small></div></div><Link className="admin-table-stack admin-table-link" href={`/admin/merchants?name=${encodeURIComponent(product.merchantName || "")}`}><strong>{product.merchantName || `商家 ${product.merchantId}`}</strong><small>{product.storeName || `店铺 ${product.storeId}`}</small></Link><Link className="admin-table-link" href={`/admin/categories?focus=${product.categoryId}`}>{product.categoryName || `分类 ${product.categoryId}`}</Link><span className="admin-table-time">{formatDateTime(product.updateTime ?? product.createTime)}</span><span className={`tag product-audit-status product-audit-status--${product.auditStatus}`}>{auditLabels[product.auditStatus] ?? product.auditStatus}</span><div className="listing-table__actions"><button aria-label={`查看 ${product.name} 操作记录`} className="icon-button" title="操作记录" type="button" onClick={() => setHistoryTarget(product)}><History aria-hidden="true" size={16} /></button><button aria-label={`查看 ${product.name}`} className="icon-button" title="查看审核详情" type="button" onClick={() => void openDetail(product.id)}><Eye aria-hidden="true" size={16} /></button>{product.auditStatus === 1 ? <button aria-label={`通过 ${product.name}`} className="icon-button" title="审核通过" type="button" onClick={() => void openReview(product, "approve")}><CheckCircle2 aria-hidden="true" size={16} /></button> : null}{product.auditStatus === 1 ? <button aria-label={`驳回 ${product.name}`} className="icon-button icon-button--danger" title="驳回商品" type="button" onClick={() => void openReview(product, "reject")}><XCircle aria-hidden="true" size={16} /></button> : null}</div></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={(page) => update({ page })} />
      </div>

      <Drawer open={Boolean(detail)} title="商品审核详情" description={detail ? `${detail.name} · ${detail.code}` : ""} onClose={() => setDetail(null)}>{detail ? <ProductReviewDetail product={detail} /> : null}{detail ? <div className="drawer-form__actions"><button className="button button--secondary button--icon-label" type="button" onClick={() => { setHistoryTarget(detail); setDetail(null); }}><History aria-hidden="true" size={16} />操作记录</button>{detail.auditStatus === 1 ? <><button className="button button--danger button--icon-label" type="button" onClick={() => void openReview(detail, "reject")}><XCircle aria-hidden="true" size={16} />驳回商品</button><button className="button button--primary button--icon-label" type="button" onClick={() => void openReview(detail, "approve")}><CheckCircle2 aria-hidden="true" size={16} />审核通过</button></> : null}</div> : null}</Drawer>

      <Drawer open={Boolean(reviewTarget)} title={reviewMode === "approve" ? "确认商品合规" : "填写商品驳回意见"} description={reviewTarget ? `${reviewTarget.name} · ${reviewTarget.merchantName || "商家商品"}` : ""} busy={busy} onClose={() => setReviewTarget(null)}>{reviewTarget ? <div className="drawer-form"><ProductReviewDetail product={reviewTarget} compact />{reviewMode === "reject" ? <label className="field"><span>驳回原因</span><textarea maxLength={255} placeholder="指出需要修改的图片、描述、分类或 SKU 信息" rows={5} value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} /></label> : <Notice tone="info">确认商品资料、分类、图片和 SKU 信息符合平台发布要求。</Notice>}<div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setReviewTarget(null)}>取消</button><button className={reviewMode === "reject" ? "button button--danger" : "button button--primary"} disabled={busy} type="button" onClick={() => { if (reviewMode === "approve" || rejectReason.trim()) setConfirmReview(true); else setError("请填写驳回原因"); }}>{reviewMode === "approve" ? "提交通过" : "提交驳回"}</button></div></div> : null}</Drawer>
      <ConfirmDialog open={confirmReview} title={reviewMode === "approve" ? "确认通过商品审核" : "确认驳回商品"} description={reviewMode === "approve" ? `通过后“${reviewTarget?.name ?? ""}”可由商家上架销售。` : `驳回后“${reviewTarget?.name ?? ""}”将退回商家修改，驳回原因会向商家展示。`} confirmLabel={reviewMode === "approve" ? "确认通过" : "确认驳回"} dangerous={reviewMode === "reject"} busy={busy} onClose={() => setConfirmReview(false)} onConfirm={() => void review()} />
      <ConfirmDialog open={confirmBatch} title="批量通过商品审核" description={`将审核通过已选择的 ${selected.size} 件商品。处理过程中单件失败不会回滚其他成功商品。`} confirmLabel="确认批量通过" busy={busy} onClose={() => setConfirmBatch(false)} onConfirm={() => void approveBatch()} />
      <ProductOperationHistory open={Boolean(historyTarget)} productId={historyTarget?.id} productName={historyTarget?.name} scope="admin" onClose={() => setHistoryTarget(null)} />
    </section>
  );
}

function ProductReviewDetail({ product, compact = false }: { product: ProductAdmin; compact?: boolean }) {
  const merchantHref = `/admin/merchants?name=${encodeURIComponent(product.merchantName || "")}`;
  return <div className={compact ? "product-review-drawer product-review-drawer--compact" : "product-review-drawer"}><div className="product-review-summary"><MediaImage alt={product.name} fallback={<span className="listing-table__placeholder">C</span>} src={assetUrl(product.mainImageUrl)} /><div><strong>{product.name}</strong><small>{product.subtitle || "无副标题"}</small><span>{product.skus.length} 个 SKU · {product.skus.length ? `${formatMoney(Math.min(...product.skus.map((sku) => sku.price)))} 起` : "暂无 SKU"}</span></div></div><div className="detail-rows"><div><span>所属商家</span><strong><Link href={merchantHref}>{product.merchantName || `商家 ${product.merchantId}`}</Link></strong></div><div><span>所属店铺</span><strong><Link href={merchantHref}>{product.storeName || `店铺 ${product.storeId}`}</Link></strong></div><div><span>商品分类</span><strong><Link href={`/admin/categories?focus=${product.categoryId}`}>{product.categoryName || `分类 ${product.categoryId}`}</Link></strong></div><div><span>商品编码</span><strong>{product.code}</strong></div>{product.rejectReason ? <div><span>上次驳回</span><strong>{product.rejectReason}</strong></div> : null}</div>{!compact ? <><div className="product-review-copy"><span>商品描述</span><p>{product.description || "—"}</p></div>{product.imageUrls?.length ? <div className="product-review-gallery">{product.imageUrls.map((url) => <MediaImage alt={`${product.name} 商品图`} fallback={<span className="listing-table__placeholder">C</span>} key={url} src={assetUrl(url)} />)}</div> : null}<div className="review-sku-table"><div className="review-sku-table__head"><span>SKU</span><span>规格</span><span>价格</span><span>库存</span></div>{product.skus.map((sku) => <div className="review-sku-table__row" key={sku.id ?? sku.code}><strong>{sku.code}</strong><span>{sku.specificationValues?.map((item) => `${item.name}: ${item.value}`).join(" / ") || "默认规格"}</span><span>{formatMoney(sku.price)}</span><span>{sku.stock}</span></div>)}</div></> : null}</div>;
}
