"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { notifyWorkspaceBadgesChanged } from "@/components/workspace-shell";
import { CurmerceApiError, assetUrl } from "@/lib/api/client";
import { personalApi } from "@/lib/api/personal";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatOrderStatus } from "@/lib/format";
import type { PersonalSellerOrder } from "@/lib/types/api";

const statusFilters = [
  { value: 0, label: "全部卖出订单" },
  { value: 10, label: "待支付" },
  { value: 20, label: "待发货" },
  { value: 30, label: "已发货" },
  { value: 40, label: "已完成" },
  { value: 50, label: "已取消" },
];
const PAGE_SIZE = 12;

export default function PersonalSellerOrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<PersonalSellerOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [shippingId, setShippingId] = useState<number | null>(null);
  const [company, setCompany] = useState("顺丰速运");
  const [trackingNo, setTrackingNo] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void loadOrders(status);
  }, [router, status, pageNo]);

  async function loadOrders(nextStatus = status) {
    setLoading(true);
    try {
      const response = await personalApi.orderPage({ pageNo, pageSize: PAGE_SIZE, status: nextStatus || undefined });
      setOrders(response?.list ?? []);
      setTotal(response?.total ?? 0);
    } catch (cause) {
      handleError(cause, "卖出订单加载失败");
    } finally {
      setLoading(false);
    }
  }

  function handleError(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearToken();
      router.replace("/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  function beginShipping(order: PersonalSellerOrder) {
    setShippingId(order.id);
    setCompany(order.logisticsCompany || "顺丰速运");
    setTrackingNo(order.trackingNo || "");
    setError(null);
    setMessage(null);
  }

  async function ship(order: PersonalSellerOrder) {
    const logisticsCompany = company.trim();
    const tracking = trackingNo.trim();
    if (!logisticsCompany || !tracking) {
      setError("请填写物流公司和物流单号");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await personalApi.ship({ id: order.id, logisticsCompany, trackingNo: tracking });
      setShippingId(null);
      setMessage(`订单 ${order.orderNo} 已发货`);
      notifyWorkspaceBadgesChanged();
      await loadOrders(status);
    } catch (cause) {
      handleError(cause, "发货失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="content-section merchant-orders-page">
      <div className="section-heading"><div><p className="eyebrow">PERSONAL SELLER · ORDERS</p><h1>我的卖出订单</h1><p>查看当前账号卖出的个人商品订单；只有已支付待发货订单可以填写物流。</p></div><div className="inline-actions"><Link className="button button--secondary" href="/personal/listings">我的闲置</Link><Link className="button button--secondary" href="/orders">买家订单</Link></div></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="order-tabs" role="tablist" aria-label="卖出订单状态">{statusFilters.map((item) => <button className={`order-tab${status === item.value ? " order-tab--active" : ""}`} key={item.value} type="button" onClick={() => { setStatus(item.value); setPageNo(1); }}>{item.label}</button>)}</div>
      <div className="orders-panel merchant-orders-panel"><div className="panel-heading"><h2>卖出订单记录</h2><span>{total} 条</span></div>{loading ? <p className="empty-state">订单加载中…</p> : null}{!loading && orders.length === 0 ? <p className="empty-state">当前筛选下没有卖出订单。</p> : null}<div className="merchant-order-list">{orders.map((order) => <article className="merchant-order-card" key={order.id}><div className="merchant-order-card__header"><div><span className="order-card__date">{formatDateTime(order.createTime)}</span><strong>订单号：{order.orderNo}</strong></div><span className={`tag order-status order-status--${order.status}`}>{formatOrderStatus(order.status)}</span></div><div className="merchant-order-card__grid"><div><p className="eyebrow">BUYER</p><strong>{order.buyerNickname || "买家"}</strong><span>{order.buyerMobile || order.buyerEmail || "—"}</span></div><div><p className="eyebrow">收货地址快照</p><strong>{order.receiverName || "—"} · {order.receiverMobile || "—"}</strong><span>{order.receiverAreaName ? `${order.receiverAreaName} · ` : ""}{order.receiverDetailAddress || "—"}</span></div><div><p className="eyebrow">AMOUNT</p><strong>{formatMoney(order.payableAmount)}</strong><span>{order.itemCount} 件商品</span></div></div><div className="merchant-item-list">{order.items?.map((item) => <div className="merchant-item" key={item.id}><div className="merchant-item__image"><MediaImage alt={item.productName} fallback={<span>C</span>} src={assetUrl(item.skuImageUrl || item.productImageUrl)} /></div><div><strong>{item.productName}</strong><span>{item.specificationValues?.map((value) => `${value.name}: ${value.value}`).join(" / ") || "默认规格"} · ×{item.quantity}</span></div><strong>{formatMoney(item.totalAmount)}</strong></div>)}</div>{shippingId === order.id ? <div className="shipping-form"><label className="field"><span>物流公司</span><input maxLength={64} value={company} onChange={(event) => setCompany(event.target.value)} /></label><label className="field"><span>物流单号</span><input maxLength={64} value={trackingNo} onChange={(event) => setTrackingNo(event.target.value)} /></label><div className="inline-actions"><button className="button button--primary" disabled={busy} type="button" onClick={() => void ship(order)}>{busy ? "提交中…" : "确认发货"}</button><button className="button button--secondary" disabled={busy} type="button" onClick={() => setShippingId(null)}>取消</button></div></div> : order.status === 20 ? <button className="button button--primary" type="button" onClick={() => beginShipping(order)}>填写物流并发货</button> : null}</article>)}</div><Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} /></div>
    </section>
  );
}
