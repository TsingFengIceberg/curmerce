"use client";

import { ClipboardCheck, Download, History, PackageOpen, Pencil, Plus, Upload } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { ProductOperationHistory } from "@/components/product-operation-history";
import { adminProductApi } from "@/lib/api/admin-product";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { ensureMerchantOwner } from "@/lib/auth/guards";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { ProductAdmin } from "@/lib/types/api";

const PAGE_SIZE = 15;
const auditLabels: Record<number, string> = { 0: "草稿", 1: "待审核", 2: "审核通过", 3: "已驳回" };
const saleLabels: Record<number, string> = { 0: "下架", 1: "上架" };
type Action = "submit" | "list" | "delist";

export default function MerchantProductsPage() {
  const router = useRouter();
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [auditStatus, setAuditStatus] = useState("");
  const [saleStatus, setSaleStatus] = useState("");
  const [pending, setPending] = useState<{ product: ProductAdmin; action: Action } | null>(null);
  const [historyTarget, setHistoryTarget] = useState<ProductAdmin | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void ensureMerchantOwner(router).then((allowed) => { if (allowed) void load(); });
  }, [router, pageNo, auditStatus, saleStatus]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await adminProductApi.pageOwn({ pageNo, pageSize: PAGE_SIZE, auditStatus: auditStatus ? Number(auditStatus) : undefined, saleStatus: saleStatus ? Number(saleStatus) : undefined });
      setProducts(page.list ?? []);
      setTotal(page.total ?? 0);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品列表加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function transition() {
    if (!pending) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (pending.action === "submit") await adminProductApi.submitOwn(pending.product.id);
      if (pending.action === "list") await adminProductApi.listOwn(pending.product.id);
      if (pending.action === "delist") await adminProductApi.delistOwn(pending.product.id);
      setMessage(pending.action === "submit" ? "商品已提交审核" : pending.action === "list" ? "商品已上架" : "商品已下架");
      setPending(null);
      await load();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品状态更新失败");
    } finally {
      setBusy(false);
    }
  }

  const actionMeta = pending?.action === "submit" ? { title: "提交商品审核", description: "提交后商品资料将暂时不能编辑，平台审核通过后才可上架。", label: "提交审核" }
    : pending?.action === "list" ? { title: "上架商品", description: "上架后商品会立即出现在公开商城中。", label: "确认上架" }
      : { title: "下架商品", description: "下架后商品停止销售，但不会影响已有订单。", label: "确认下架" };

  return (
    <section className="content-section admin-page">
      <div className="section-heading"><div><p className="eyebrow">MERCHANT · CATALOG</p><h1>商品管理</h1><p>维护商品资料、SKU、库存以及审核和上架状态。</p></div><Link className="button button--primary button--icon-label" href="/merchant/products/new"><Plus aria-hidden="true" size={17} />创建商品</Link></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="workspace-section merchant-product-table-panel">
        <div className="listing-toolbar"><div><strong>全部商品</strong><span>共 {total} 件</span></div><div className="inline-actions"><select aria-label="审核状态" value={auditStatus} onChange={(event) => { setAuditStatus(event.target.value); setPageNo(1); }}><option value="">全部审核状态</option><option value="0">草稿</option><option value="1">待审核</option><option value="2">审核通过</option><option value="3">已驳回</option></select><select aria-label="销售状态" value={saleStatus} onChange={(event) => { setSaleStatus(event.target.value); setPageNo(1); }}><option value="">全部销售状态</option><option value="0">下架</option><option value="1">上架</option></select></div></div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !products.length ? <EmptyState icon={<PackageOpen aria-hidden="true" size={23} />} title="还没有符合条件的商品" description="创建商品草稿并完善 SKU 后即可提交审核。" action={{ href: "/merchant/products/new", label: "创建第一件商品" }} /> : null}
        {!loading && products.length ? <div className="merchant-product-table"><div className="merchant-product-table__head"><span>商品</span><span>SKU / 库存</span><span>价格</span><span>状态</span><span>更新时间</span><span>操作</span></div>{products.map((product) => { const prices = product.skus.map((sku) => sku.price); const totalStock = product.skus.reduce((sum, sku) => sum + sku.stock, 0); return <article className="merchant-product-table__row" key={product.id}><div className="listing-table__product"><MediaImage alt={product.name} fallback={<span className="listing-table__placeholder">C</span>} src={assetUrl(product.mainImageUrl)} /><div><strong>{product.name}</strong><small>{product.code}</small>{product.rejectReason ? <em>驳回：{product.rejectReason}</em> : null}</div></div><div className="merchant-product-table__metric"><strong>{product.skus.length} 个 SKU</strong><span>总库存 {totalStock}</span></div><div className="merchant-product-table__metric"><strong>{prices.length ? formatMoney(Math.min(...prices)) : "—"}</strong><span>起售价</span></div><div className="listing-table__status"><span className="tag">{auditLabels[product.auditStatus] ?? product.auditStatus}</span><span className="tag">{saleLabels[product.saleStatus] ?? product.saleStatus}</span></div><span className="merchant-product-table__time">{formatDateTime(product.updateTime ?? product.createTime)}</span><div className="listing-table__actions"><button aria-label={`查看 ${product.name} 操作记录`} className="icon-button" title="操作记录" type="button" onClick={() => setHistoryTarget(product)}><History aria-hidden="true" size={16} /></button>{product.auditStatus === 0 || product.auditStatus === 3 ? <Link aria-label={`编辑 ${product.name}`} className="icon-button" href={`/merchant/products/${product.id}/edit`} title="编辑商品"><Pencil aria-hidden="true" size={16} /></Link> : null}{product.auditStatus === 0 || product.auditStatus === 3 ? <button aria-label={`提交 ${product.name} 审核`} className="icon-button" title="提交审核" type="button" onClick={() => setPending({ product, action: "submit" })}><ClipboardCheck aria-hidden="true" size={16} /></button> : null}{product.auditStatus === 2 && product.saleStatus === 0 ? <button aria-label={`上架 ${product.name}`} className="icon-button" title="上架" type="button" onClick={() => setPending({ product, action: "list" })}><Upload aria-hidden="true" size={16} /></button> : null}{product.saleStatus === 1 ? <button aria-label={`下架 ${product.name}`} className="icon-button icon-button--danger" title="下架" type="button" onClick={() => setPending({ product, action: "delist" })}><Download aria-hidden="true" size={16} /></button> : null}</div></article>; })}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>
      <ConfirmDialog open={Boolean(pending)} title={actionMeta.title} description={actionMeta.description} confirmLabel={actionMeta.label} dangerous={pending?.action === "delist"} busy={busy} onClose={() => { if (!busy) setPending(null); }} onConfirm={() => void transition()} />
      <ProductOperationHistory open={Boolean(historyTarget)} productId={historyTarget?.id} productName={historyTarget?.name} scope="merchant" onClose={() => setHistoryTarget(null)} />
    </section>
  );
}
