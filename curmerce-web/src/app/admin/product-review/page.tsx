"use client";

import { CheckCircle2, ClipboardCheck, Eye, History, Search, XCircle } from "lucide-react";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { ProductOperationHistory } from "@/components/product-operation-history";
import { adminProductApi } from "@/lib/api/admin-product";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { ProductAdmin } from "@/lib/types/api";

const PAGE_SIZE = 15;
const auditLabels: Record<number, string> = { 0: "草稿", 1: "待审核", 2: "审核通过", 3: "已驳回" };
type ReviewMode = "approve" | "reject";

export default function AdminProductReviewPage() {
  const router = useRouter();
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [auditStatus, setAuditStatus] = useState("1");
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
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
  }, [router, pageNo, auditStatus, keyword]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await adminProductApi.reviewPage({ pageNo, pageSize: PAGE_SIZE, auditStatus: auditStatus ? Number(auditStatus) : undefined, name: keyword });
      setProducts(page.list ?? []);
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
    setPageNo(1);
    setKeyword(keywordInput.trim());
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
        <div className="admin-data-toolbar"><select aria-label="商品审核状态" value={auditStatus} onChange={(event) => { setAuditStatus(event.target.value); setPageNo(1); }}><option value="">全部状态</option><option value="1">待审核</option><option value="2">已通过</option><option value="3">已驳回</option><option value="0">草稿</option></select><form className="order-search" onSubmit={search}><Search aria-hidden="true" size={16} /><input aria-label="商品名称" placeholder="搜索商品名称" value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} /><button type="submit">查询</button></form><span>共 {total} 件</span></div>
        {loading || detailLoading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !products.length ? <EmptyState icon={<ClipboardCheck aria-hidden="true" size={23} />} title="没有符合条件的商品" description="调整审核状态或搜索条件后重新查询。" /> : null}
        {!loading && products.length ? <div className="admin-product-review-table"><div className="admin-product-review-table__head"><span>商品</span><span>商家 / 店铺</span><span>分类</span><span>提交时间</span><span>审核状态</span><span>操作</span></div>{products.map((product) => <article className="admin-product-review-table__row" key={product.id}><div className="admin-product-review-table__product">{assetUrl(product.mainImageUrl) ? <img alt={product.name} src={assetUrl(product.mainImageUrl) ?? ""} /> : <span className="listing-table__placeholder">C</span>}<div><strong>{product.name}</strong><small>{product.code}</small></div></div><Link className="admin-table-stack admin-table-link" href={`/admin/merchants?name=${encodeURIComponent(product.merchantName || "")}`}><strong>{product.merchantName || `商家 ${product.merchantId}`}</strong><small>{product.storeName || `店铺 ${product.storeId}`}</small></Link><Link className="admin-table-link" href={`/admin/categories?focus=${product.categoryId}`}>{product.categoryName || `分类 ${product.categoryId}`}</Link><span className="admin-table-time">{formatDateTime(product.updateTime ?? product.createTime)}</span><span className={`tag product-audit-status product-audit-status--${product.auditStatus}`}>{auditLabels[product.auditStatus] ?? product.auditStatus}</span><div className="listing-table__actions"><button aria-label={`查看 ${product.name} 操作记录`} className="icon-button" title="操作记录" type="button" onClick={() => setHistoryTarget(product)}><History aria-hidden="true" size={16} /></button><button aria-label={`查看 ${product.name}`} className="icon-button" title="查看审核详情" type="button" onClick={() => void openDetail(product.id)}><Eye aria-hidden="true" size={16} /></button>{product.auditStatus === 1 ? <button aria-label={`通过 ${product.name}`} className="icon-button" title="审核通过" type="button" onClick={() => void openReview(product, "approve")}><CheckCircle2 aria-hidden="true" size={16} /></button> : null}{product.auditStatus === 1 ? <button aria-label={`驳回 ${product.name}`} className="icon-button icon-button--danger" title="驳回商品" type="button" onClick={() => void openReview(product, "reject")}><XCircle aria-hidden="true" size={16} /></button> : null}</div></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>

      <Drawer open={Boolean(detail)} title="商品审核详情" description={detail ? `${detail.name} · ${detail.code}` : ""} onClose={() => setDetail(null)}>{detail ? <ProductReviewDetail product={detail} /> : null}{detail ? <div className="drawer-form__actions"><button className="button button--secondary button--icon-label" type="button" onClick={() => { setHistoryTarget(detail); setDetail(null); }}><History aria-hidden="true" size={16} />操作记录</button>{detail.auditStatus === 1 ? <><button className="button button--danger button--icon-label" type="button" onClick={() => void openReview(detail, "reject")}><XCircle aria-hidden="true" size={16} />驳回商品</button><button className="button button--primary button--icon-label" type="button" onClick={() => void openReview(detail, "approve")}><CheckCircle2 aria-hidden="true" size={16} />审核通过</button></> : null}</div> : null}</Drawer>

      <Drawer open={Boolean(reviewTarget)} title={reviewMode === "approve" ? "确认商品合规" : "填写商品驳回意见"} description={reviewTarget ? `${reviewTarget.name} · ${reviewTarget.merchantName || "商家商品"}` : ""} busy={busy} onClose={() => setReviewTarget(null)}>{reviewTarget ? <div className="drawer-form"><ProductReviewDetail product={reviewTarget} compact />{reviewMode === "reject" ? <label className="field"><span>驳回原因</span><textarea maxLength={255} placeholder="指出需要修改的图片、描述、分类或 SKU 信息" rows={5} value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} /></label> : <Notice tone="info">确认商品资料、分类、图片和 SKU 信息符合平台发布要求。</Notice>}<div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setReviewTarget(null)}>取消</button><button className={reviewMode === "reject" ? "button button--danger" : "button button--primary"} disabled={busy} type="button" onClick={() => { if (reviewMode === "approve" || rejectReason.trim()) setConfirmReview(true); else setError("请填写驳回原因"); }}>{reviewMode === "approve" ? "提交通过" : "提交驳回"}</button></div></div> : null}</Drawer>
      <ConfirmDialog open={confirmReview} title={reviewMode === "approve" ? "确认通过商品审核" : "确认驳回商品"} description={reviewMode === "approve" ? `通过后“${reviewTarget?.name ?? ""}”可由商家上架销售。` : `驳回后“${reviewTarget?.name ?? ""}”将退回商家修改，驳回原因会向商家展示。`} confirmLabel={reviewMode === "approve" ? "确认通过" : "确认驳回"} dangerous={reviewMode === "reject"} busy={busy} onClose={() => setConfirmReview(false)} onConfirm={() => void review()} />
      <ProductOperationHistory open={Boolean(historyTarget)} productId={historyTarget?.id} productName={historyTarget?.name} scope="admin" onClose={() => setHistoryTarget(null)} />
    </section>
  );
}

function ProductReviewDetail({ product, compact = false }: { product: ProductAdmin; compact?: boolean }) {
  const merchantHref = `/admin/merchants?name=${encodeURIComponent(product.merchantName || "")}`;
  return <div className={compact ? "product-review-drawer product-review-drawer--compact" : "product-review-drawer"}><div className="product-review-summary">{assetUrl(product.mainImageUrl) ? <img alt={product.name} src={assetUrl(product.mainImageUrl) ?? ""} /> : <span className="listing-table__placeholder">C</span>}<div><strong>{product.name}</strong><small>{product.subtitle || "无副标题"}</small><span>{product.skus.length} 个 SKU · {product.skus.length ? `${formatMoney(Math.min(...product.skus.map((sku) => sku.price)))} 起` : "暂无 SKU"}</span></div></div><div className="detail-rows"><div><span>所属商家</span><strong><Link href={merchantHref}>{product.merchantName || `商家 ${product.merchantId}`}</Link></strong></div><div><span>所属店铺</span><strong><Link href={merchantHref}>{product.storeName || `店铺 ${product.storeId}`}</Link></strong></div><div><span>商品分类</span><strong><Link href={`/admin/categories?focus=${product.categoryId}`}>{product.categoryName || `分类 ${product.categoryId}`}</Link></strong></div><div><span>商品编码</span><strong>{product.code}</strong></div>{product.rejectReason ? <div><span>上次驳回</span><strong>{product.rejectReason}</strong></div> : null}</div>{!compact ? <><div className="product-review-copy"><span>商品描述</span><p>{product.description || "—"}</p></div>{product.imageUrls?.length ? <div className="product-review-gallery">{product.imageUrls.map((url) => assetUrl(url) ? <img alt={`${product.name} 商品图`} key={url} src={assetUrl(url) ?? ""} /> : null)}</div> : null}<div className="review-sku-table"><div className="review-sku-table__head"><span>SKU</span><span>规格</span><span>价格</span><span>库存</span></div>{product.skus.map((sku) => <div className="review-sku-table__row" key={sku.id ?? sku.code}><strong>{sku.code}</strong><span>{sku.specificationValues?.map((item) => `${item.name}: ${item.value}`).join(" / ") || "默认规格"}</span><span>{formatMoney(sku.price)}</span><span>{sku.stock}</span></div>)}</div></> : null}</div>;
}
