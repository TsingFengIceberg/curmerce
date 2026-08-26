"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { FlaskConical, Search, ShoppingBag } from "lucide-react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { CopyButton } from "@/components/copy-button";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { orderApi } from "@/lib/api/order";
import { paymentApi } from "@/lib/api/payment";
import { CurmerceApiError } from "@/lib/api/client";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatOrderStatus, formatRefundStatus } from "@/lib/format";
import type { OrderSummary } from "@/lib/types/api";

const PAGE_SIZE = 10;
const statusFilters = [
  { value: 0, label: "全部" },
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
  const [counts, setCounts] = useState<Record<number, number>>({});
  const [status, setStatus] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [orderNoInput, setOrderNoInput] = useState("");
  const [orderNo, setOrderNo] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [pendingCancel, setPendingCancel] = useState<OrderSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) { router.replace("/login"); return; }
    void loadOrders();
  }, [router, status, pageNo, orderNo]);

  useEffect(() => {
    if (!getAccessToken()) return;
    void loadCounts();
  }, [router]);

  async function loadOrders() {
    setLoading(true); setError(null);
    try {
      const response = await orderApi.page({ pageNo, pageSize: PAGE_SIZE, status: status || undefined, orderNo });
      setOrders(response?.list ?? []); setTotal(response?.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) { clearToken(); router.replace("/login"); return; }
      setError(cause instanceof CurmerceApiError ? cause.message : "订单加载失败");
    } finally { setLoading(false); }
  }

  async function loadCounts() {
    try {
      const pages = await Promise.all(statusFilters.map((item) => orderApi.page({ pageNo: 1, pageSize: 1, status: item.value || undefined })));
      setCounts(Object.fromEntries(statusFilters.map((item, index) => [item.value, pages[index]?.total ?? 0])));
    } catch {
      // Counts are supplementary; the primary list reports actionable errors.
    }
  }

  async function pay(order: OrderSummary) {
    setBusyId(order.id); setError(null); setMessage(null);
    try {
      const payment = await paymentApi.create(order.id);
      await paymentApi.simulateCallback({ paymentNo: payment.paymentNo, callbackId: `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`, paidAmount: payment.amount });
      setMessage(`订单 ${order.orderNo} 已完成测试支付`);
      await Promise.all([loadOrders(), loadCounts()]);
    } catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "测试支付失败"); }
    finally { setBusyId(null); }
  }

  async function cancel() {
    const order = pendingCancel;
    if (!order) return;
    setBusyId(order.id); setPendingCancel(null); setError(null); setMessage(null);
    try {
      await orderApi.cancel(order.id); setMessage(`订单 ${order.orderNo} 已取消`);
      await Promise.all([loadOrders(), loadCounts()]);
    } catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "取消订单失败"); }
    finally { setBusyId(null); }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPageNo(1); setOrderNo(orderNoInput.trim());
  }

  return (
    <section className="content-section orders-page orders-page--product">
      <div className="section-heading"><div><p className="eyebrow">MY PURCHASES</p><h1>买入订单</h1><p>跟踪支付、发货、收货和售后状态。</p></div><Link className="button button--secondary" href="/catalog"><ShoppingBag aria-hidden="true" size={18} />继续购物</Link></div>
      {message ? <Notice tone="success">{message}</Notice> : null}{error ? <Notice>{error}</Notice> : null}
      <div className="order-command-bar">
        <div className="order-tabs" role="tablist" aria-label="订单状态筛选">{statusFilters.map((item) => <button aria-selected={status === item.value} className={`order-tab${status === item.value ? " order-tab--active" : ""}`} key={item.value} role="tab" type="button" onClick={() => { setStatus(item.value); setPageNo(1); }}>{item.label}<span>{counts[item.value] ?? "·"}</span></button>)}</div>
        <form className="order-search" onSubmit={search}><Search aria-hidden="true" size={17} /><input aria-label="搜索订单号" placeholder="搜索订单号" value={orderNoInput} onChange={(event) => setOrderNoInput(event.target.value)} /><button type="submit">查询</button></form>
      </div>
      <div className="orders-panel orders-panel--compact">
        <div className="panel-heading"><h2>订单记录</h2><span>{total} 条</span></div>
        {loading ? <div className="order-list-skeleton">{Array.from({ length: 4 }, (_, index) => <span key={index} />)}</div> : orders.length === 0 ? <EmptyState title="当前条件下没有订单" description={orderNo ? "检查订单号或清空搜索条件。" : "选购商品后，订单会出现在这里。"} action={{ href: orderNo ? "/orders" : "/catalog", label: orderNo ? "查看全部订单" : "去逛商城" }} /> : <div className="order-list order-list--compact">{orders.map((order) => <article className="order-row" key={order.id}><div className="order-row__identity"><span>{formatDateTime(order.createTime)}</span><div><strong>{order.orderNo}</strong><CopyButton value={order.orderNo} label="复制" /></div></div><div className="order-row__summary"><strong>{order.itemCount} 件商品</strong><span>{order.sellerType === 2 ? "个人闲置" : "商家订单"}</span></div><div className="order-row__amount"><strong>{formatMoney(order.payableAmount)}</strong>{order.refundStatus ? <span>{formatRefundStatus(order.refundStatus)}</span> : null}</div><span className={`tag order-status order-status--${order.status}`}>{formatOrderStatus(order.status)}</span><div className="order-row__actions"><Link className="button button--secondary button--small" href={`/orders/${order.id}`}>查看订单</Link>{order.status === 10 ? <><details className="test-tool-menu"><summary><FlaskConical aria-hidden="true" size={15} />测试支付</summary><div><p>演示环境会模拟支付渠道成功回调。</p><button disabled={busyId === order.id} type="button" onClick={() => void pay(order)}>{busyId === order.id ? "处理中…" : "模拟支付成功"}</button></div></details><button className="text-button text-button--danger" disabled={busyId === order.id} type="button" onClick={() => setPendingCancel(order)}>取消订单</button></> : null}</div></article>)}</div>}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>
      <ConfirmDialog open={pendingCancel !== null} dangerous title="取消这笔订单？" description={pendingCancel ? `订单 ${pendingCancel.orderNo} 将被取消，库存会按交易规则恢复。` : ""} confirmLabel="确认取消" busy={pendingCancel ? busyId === pendingCancel.id : false} onClose={() => setPendingCancel(null)} onConfirm={() => void cancel()} />
    </section>
  );
}
