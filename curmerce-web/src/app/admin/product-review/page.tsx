"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { adminProductApi } from "@/lib/api/admin-product";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { ProductAdmin } from "@/lib/types/api";

const auditLabels: Record<number, string> = { 0: "草稿", 1: "待审核", 2: "审核通过", 3: "已驳回" };
const saleLabels: Record<number, string> = { 0: "下架", 1: "上架" };

export default function AdminProductReviewPage() {
  const router = useRouter();
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [selected, setSelected] = useState<ProductAdmin | null>(null);
  const [auditStatus, setAuditStatus] = useState("1");
  const [rejectReason, setRejectReason] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAdminAccessToken()) {
      router.replace("/merchant/login");
      return;
    }
    void loadProducts(auditStatus);
  }, [router, auditStatus]);

  async function loadProducts(nextAuditStatus = auditStatus) {
    setLoading(true);
    setError(null);
    try {
      const response = await adminProductApi.reviewPage({ pageNo: 1, pageSize: 20, auditStatus: nextAuditStatus ? Number(nextAuditStatus) : undefined });
      const list = response?.list ?? [];
      setProducts(list);
      if (selected) {
        const refreshed = list.find((product) => product.id === selected.id);
        if (refreshed) await loadDetail(refreshed.id);
      }
    } catch (cause) {
      handleError(cause, "商品审核列表加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function loadDetail(id: number) {
    try {
      const detail = await adminProductApi.reviewDetail(id);
      setSelected(detail);
      setRejectReason(detail.rejectReason ?? "");
    } catch (cause) {
      handleError(cause, "商品审核详情加载失败");
    }
  }

  function handleError(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      router.replace("/merchant/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  async function review(action: "approve" | "reject") {
    if (!selected) return;
    if (action === "reject" && !rejectReason.trim()) {
      setError("驳回商品时必须填写原因");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (action === "approve") await adminProductApi.approve(selected.id);
      else await adminProductApi.reject(selected.id, rejectReason);
      setMessage(action === "approve" ? "商品审核已通过" : "商品已驳回");
      await loadProducts();
      setSelected(null);
    } catch (cause) {
      handleError(cause, "商品审核操作失败");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    clearAdminToken();
    router.replace("/merchant/login");
  }

  return (
    <section className="content-section admin-page product-review-page">
      <div className="section-heading">
        <div><p className="eyebrow">ADMIN · PRODUCT REVIEW</p><h1>商品审核与生命周期</h1><p>审核商家提交的商品，并将审核结果交回商家上架或下架。</p></div>
        <div className="inline-actions"><Link className="button button--secondary" href="/admin/categories">商品分类</Link><Link className="button button--secondary" href="/admin/refunds">退款审核</Link><button className="button button--secondary" type="button" onClick={() => void logout()}>退出后台</button></div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="admin-toolbar"><div className="order-tabs" role="tablist" aria-label="商品审核状态"><button className={`order-tab${auditStatus === "" ? " order-tab--active" : ""}`} type="button" onClick={() => setAuditStatus("")}>全部</button><button className={`order-tab${auditStatus === "1" ? " order-tab--active" : ""}`} type="button" onClick={() => setAuditStatus("1")}>待审核</button><button className={`order-tab${auditStatus === "2" ? " order-tab--active" : ""}`} type="button" onClick={() => setAuditStatus("2")}>已通过</button><button className={`order-tab${auditStatus === "3" ? " order-tab--active" : ""}`} type="button" onClick={() => setAuditStatus("3")}>已驳回</button></div></div>
      <div className="admin-split-layout">
        <div className="orders-panel"><div className="panel-heading"><h2>商品审核列表</h2><span>{products.length} 条</span></div>{loading ? <p className="empty-state">商品审核列表加载中…</p> : null}{!loading && products.length === 0 ? <p className="empty-state">当前没有商品审核记录。</p> : null}<div className="admin-record-list">{products.map((product) => <button className={`admin-record-card${selected?.id === product.id ? " admin-record-card--active" : ""}`} key={product.id} type="button" onClick={() => void loadDetail(product.id)}><div className="admin-record-card__top"><strong>{product.name}</strong><span className="tag product-status">{auditLabels[product.auditStatus] ?? `审核 ${product.auditStatus}`}</span></div><div className="admin-record-card__meta"><span>{product.code}</span><span>{formatDateTime(product.createTime)}</span></div><div className="admin-record-card__bottom"><span>{product.skus.length} 个 SKU · {saleLabels[product.saleStatus] ?? `销售 ${product.saleStatus}`}</span><strong>{product.skus.length ? formatMoney(Math.min(...product.skus.map((sku) => sku.price))) : "—"}</strong></div></button>)}</div></div>
        <div className="orders-panel admin-detail-panel">{!selected ? <p className="empty-state">选择商品查看审核详情。</p> : <><div className="panel-heading"><h2>{selected.name}</h2><span className="tag product-status">{auditLabels[selected.auditStatus] ?? `审核 ${selected.auditStatus}`}</span></div><div className="product-review-detail">{selected.mainImageUrl ? <img className="product-review-detail__image" src={selected.mainImageUrl} alt={selected.name} /> : null}<div className="detail-rows"><div><span>商品编码</span><strong>{selected.code}</strong></div><div><span>商家 / 店铺</span><strong>{selected.merchantId} / {selected.storeId}</strong></div><div><span>分类编号</span><strong>{selected.categoryId}</strong></div><div><span>副标题</span><strong>{selected.subtitle || "—"}</strong></div><div><span>审核时间</span><strong>{formatDateTime(selected.reviewTime)}</strong></div><div><span>驳回原因</span><strong>{selected.rejectReason || "—"}</strong></div></div><div className="product-review-description"><span>商品描述</span><p>{selected.description || "—"}</p></div><div className="product-review-skus"><h3>SKU</h3>{selected.skus.map((sku) => <div className="product-review-sku" key={sku.id ?? sku.code}><div><strong>{sku.code}</strong><span>{sku.specificationValues?.map((item) => `${item.name}: ${item.value}`).join(" / ") || "默认规格"}</span></div><span>{formatMoney(sku.price)} · 库存 {sku.stock}</span></div>)}</div></div>{selected.auditStatus === 1 ? <div className="admin-action-box"><label className="field"><span>驳回原因</span><textarea maxLength={255} onChange={(event) => setRejectReason(event.target.value)} placeholder="请填写明确的修改原因" rows={3} value={rejectReason} /></label><div className="inline-actions"><button className="button button--primary" disabled={busy} type="button" onClick={() => void review("approve")}>审核通过</button><button className="button button--danger" disabled={busy} type="button" onClick={() => void review("reject")}>驳回商品</button></div></div> : null}</>}
        </div>
      </div>
    </section>
  );
}
