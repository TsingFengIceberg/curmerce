"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { CurmerceApiError, assetUrl } from "@/lib/api/client";
import { adminAuthApi } from "@/lib/api/admin-auth";
import { adminOrderApi } from "@/lib/api/admin-order";
import { clearAdminToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatOrderStatus } from "@/lib/format";
import type { MerchantOrder } from "@/lib/types/api";
import { ensureMerchantOwner } from "@/lib/auth/guards";

export default function MerchantOrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<MerchantOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [orderNo, setOrderNo] = useState("");
  const [orderNoInput, setOrderNoInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [shippingId, setShippingId] = useState<number | null>(null);
  const [logisticsCompany, setLogisticsCompany] = useState("顺丰速运");
  const [trackingNo, setTrackingNo] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void ensureMerchantOwner(router).then((allowed) => {
      if (allowed) void loadOrders();
    });
  }, [router]);

  async function loadOrders(nextStatus = status, nextOrderNo = orderNo) {
    setLoading(true);
    setError(null);
    try {
      const response = await adminOrderApi.pageOwn({ pageNo: 1, pageSize: 20, status: nextStatus, orderNo: nextOrderNo });
      setOrders(response?.list ?? []);
      setTotal(response?.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearAdminToken();
        router.replace("/merchant/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "商家订单加载失败");
    } finally {
      setLoading(false);
    }
  }

  function filterByStatus(value: string) {
    const nextStatus = value ? Number(value) : undefined;
    setStatus(nextStatus);
    void loadOrders(nextStatus, orderNo);
  }

  function searchOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextOrderNo = orderNoInput.trim();
    setOrderNo(nextOrderNo);
    void loadOrders(status, nextOrderNo);
  }

  function beginShipping(order: MerchantOrder) {
    setShippingId(order.id);
    setLogisticsCompany(order.logisticsCompany || "顺丰速运");
    setTrackingNo(order.trackingNo || "");
    setError(null);
    setMessage(null);
  }

  async function ship(order: MerchantOrder) {
    const company = logisticsCompany.trim();
    const tracking = trackingNo.trim();
    if (!company || !tracking) {
      setError("请填写物流公司和物流单号");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await adminOrderApi.shipOwn({ id: order.id, logisticsCompany: company, trackingNo: tracking });
      setShippingId(null);
      setMessage(`订单 ${order.orderNo} 已发货`);
      await loadOrders(status, orderNo);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearAdminToken();
        router.replace("/merchant/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "发货失败");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    await adminAuthApi.logout();
    router.replace("/merchant/login");
  }

  return (
    <section className="content-section merchant-orders-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">MERCHANT · FULFILLMENT</p>
          <h1>订单管理</h1>
          <p>当前商家上下文共 {total} 笔订单。数据范围由后端权限和商家、店铺归属决定。</p>
        </div>
        <div className="inline-actions"><Link className="button button--secondary" href="/merchant/store">店铺资料</Link><Link className="button button--secondary" href="/merchant/products">商品管理</Link><Link className="button button--secondary" href="/merchant/refunds">退款审核</Link><button className="button button--secondary" type="button" onClick={() => void logout()}>退出后台</button><Link className="button button--secondary" href="/catalog">查看商城</Link></div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="orders-panel merchant-orders-panel">
        <div className="panel-heading"><h2>自己的订单</h2><span>{total} 条</span></div>
        <div className="admin-toolbar">
          <select aria-label="订单状态" value={status ?? ""} onChange={(event) => filterByStatus(event.target.value)}>
            <option value="">全部状态</option>
            <option value="10">待支付</option>
            <option value="20">已支付待发货</option>
            <option value="30">已发货</option>
            <option value="40">已完成</option>
            <option value="50">已取消</option>
          </select>
          <form className="admin-search" onSubmit={searchOrder}>
            <input aria-label="订单号" placeholder="按订单号查询" value={orderNoInput} onChange={(event) => setOrderNoInput(event.target.value)} />
            <button className="button button--secondary" type="submit">查询</button>
          </form>
        </div>
        {loading ? <p className="empty-state">订单加载中…</p> : null}
        {!loading && orders.length === 0 ? <p className="empty-state">当前筛选条件下没有订单。</p> : null}
        {!loading && orders.length > 0 ? (
          <div className="merchant-order-list">
            {orders.map((order) => (
              <article className="merchant-order-card" key={order.id}>
                <div className="merchant-order-card__header">
                  <div><span className="order-card__date">{formatDateTime(order.createTime)}</span><strong>订单号：{order.orderNo}</strong></div>
                  <span className={`tag order-status order-status--${order.status}`}>{formatOrderStatus(order.status)}</span>
                </div>
                <div className="merchant-order-card__grid">
                  <div><p className="eyebrow">BUYER</p><strong>{order.buyerNickname || "买家"}</strong><span>{order.buyerMobile || order.buyerEmail || "—"}</span></div>
                  <div><p className="eyebrow">SHIPPING SNAPSHOT</p><strong>{order.receiverName || "—"} · {order.receiverMobile || "—"}</strong><span>{order.receiverAreaName ? `${order.receiverAreaName} · ` : ""}{order.receiverDetailAddress || "—"}</span></div>
                  <div><p className="eyebrow">AMOUNT</p><strong>{formatMoney(order.payableAmount)}</strong><span>{order.itemCount} 件商品</span></div>
                </div>
                <div className="merchant-item-list">
                  {order.items?.map((item) => {
                    const image = assetUrl(item.skuImageUrl || item.productImageUrl);
                    return <div className="merchant-item" key={item.id}><div className="merchant-item__image">{image ? <img src={image} alt={item.productName} /> : <span>C</span>}</div><div><strong>{item.productName}</strong><span>{item.specificationValues?.map((value) => `${value.name}: ${value.value}`).join(" / ") || "默认规格"} · ×{item.quantity}</span></div><strong>{formatMoney(item.totalAmount)}</strong></div>;
                  })}
                </div>
                {shippingId === order.id ? (
                  <div className="shipping-form">
                    <label className="field"><span>物流公司</span><input maxLength={64} onChange={(event) => setLogisticsCompany(event.target.value)} value={logisticsCompany} /></label>
                    <label className="field"><span>物流单号</span><input maxLength={64} onChange={(event) => setTrackingNo(event.target.value)} value={trackingNo} /></label>
                    <div className="inline-actions"><button className="button button--primary" disabled={busy} type="button" onClick={() => void ship(order)}>{busy ? "提交中…" : "确认发货"}</button><button className="button button--secondary" disabled={busy} type="button" onClick={() => setShippingId(null)}>取消</button></div>
                  </div>
                ) : order.status === 20 ? <button className="button button--primary" type="button" onClick={() => beginShipping(order)}>填写物流并发货</button> : order.status >= 30 ? <div className="shipping-summary">物流：{order.logisticsCompany || "—"} · 单号：{order.trackingNo || "—"}</div> : null}
              </article>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  );
}
