"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { orderApi } from "@/lib/api/order";
import { paymentApi } from "@/lib/api/payment";
import { CurmerceApiError } from "@/lib/api/client";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatOrderStatus } from "@/lib/format";
import type { OrderSummary } from "@/lib/types/api";

const statusFilters = [
  { value: 0, label: "全部订单" },
  { value: 10, label: "待支付" },
  { value: 20, label: "待发货" },
  { value: 30, label: "已发货" },
  { value: 40, label: "已完成" },
  { value: 50, label: "已取消" },
];

export default function OrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void loadOrders(status);
  }, [router, status]);

  async function loadOrders(nextStatus = status) {
    setLoading(true);
    setError(null);
    try {
      const response = await orderApi.page({ pageNo: 1, pageSize: 20, status: nextStatus || undefined });
      setOrders(response?.list ?? []);
      setTotal(response?.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "订单加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function pay(order: OrderSummary) {
    setBusyId(order.id);
    setError(null);
    setMessage(null);
    try {
      const payment = await paymentApi.create(order.id);
      const callbackId = `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
      await paymentApi.simulateCallback({
        paymentNo: payment.paymentNo,
        callbackId,
        paidAmount: payment.amount,
      });
      setMessage(`订单 ${order.orderNo} 已完成模拟支付`);
      await loadOrders(status);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "模拟支付失败");
    } finally {
      setBusyId(null);
    }
  }

  async function cancel(order: OrderSummary) {
    if (!window.confirm(`确定取消订单 ${order.orderNo} 吗？`)) return;
    setBusyId(order.id);
    setError(null);
    setMessage(null);
    try {
      await orderApi.cancel(order.id);
      setMessage(`订单 ${order.orderNo} 已取消`);
      await loadOrders(status);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "取消订单失败");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="content-section orders-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">ACCOUNT · ORDERS</p>
          <h1>我的订单</h1>
          <p>查看订单状态、发货信息，并完成模拟支付和确认收货。</p>
        </div>
        <div className="inline-actions"><Link className="button button--secondary" href="/personal/orders">卖出订单</Link><Link className="button button--secondary" href="/catalog">继续购物</Link></div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="order-tabs" role="tablist" aria-label="订单状态筛选">
        {statusFilters.map((item) => (
          <button
            className={`order-tab${status === item.value ? " order-tab--active" : ""}`}
            key={item.value}
            type="button"
            onClick={() => setStatus(item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>
      <div className="orders-panel">
        <div className="panel-heading"><h2>订单记录</h2><span>{total} 条</span></div>
        {loading ? <p className="empty-state">订单加载中…</p> : null}
        {!loading && orders.length === 0 ? <p className="empty-state">当前筛选下还没有订单。</p> : null}
        {!loading && orders.length > 0 ? (
          <div className="order-list">
            {orders.map((order) => (
              <article className="order-card" key={order.id}>
                <div className="order-card__header">
                  <div>
                    <span className="order-card__date">{formatDateTime(order.createTime)}</span>
                    <strong>订单号：{order.orderNo}</strong>
                  </div>
                  <span className={`tag order-status order-status--${order.status}`}>{formatOrderStatus(order.status)}</span>
                </div>
                <div className="order-card__body">
                  <span>{order.itemCount} 件商品</span>
                  <strong>{formatMoney(order.payableAmount)}</strong>
                </div>
                <div className="order-card__actions">
                  <Link className="text-button" href={`/orders/${order.id}`}>查看详情</Link>
                  {order.status === 10 ? (
                    <>
                      <button className="button button--primary button--small" disabled={busyId === order.id} type="button" onClick={() => void pay(order)}>
                        {busyId === order.id ? "支付中…" : "模拟支付"}
                      </button>
                      <button className="text-button text-button--danger" disabled={busyId === order.id} type="button" onClick={() => void cancel(order)}>取消订单</button>
                    </>
                  ) : null}
                </div>
              </article>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  );
}
