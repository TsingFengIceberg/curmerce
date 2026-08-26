"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { notifyWorkspaceBadgesChanged } from "@/components/workspace-shell";
import { MediaImage } from "@/components/media-image";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { CopyButton } from "@/components/copy-button";
import { CheckCircle2, Circle, FlaskConical } from "lucide-react";
import { CurmerceApiError, assetUrl } from "@/lib/api/client";
import { orderApi } from "@/lib/api/order";
import { paymentApi } from "@/lib/api/payment";
import { refundApi } from "@/lib/api/refund";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatOrderStatus, formatPaymentStatus, formatRefundStatus } from "@/lib/format";
import type { OrderDetail } from "@/lib/types/api";

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [refundReason, setRefundReason] = useState("");
  const [showRefundForm, setShowRefundForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<"cancel" | "receipt" | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    const id = Number(params.id);
    if (!Number.isInteger(id) || id < 1) {
      setError("订单编号不正确");
      setLoading(false);
      return;
    }
    void loadOrder(id);
  }, [params.id, router]);

  async function loadOrder(id = Number(params.id)) {
    setLoading(true);
    setError(null);
    try {
      setOrder(await orderApi.detail(id));
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "订单详情加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function pay() {
    if (!order) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const payment = await paymentApi.create(order.id);
      await paymentApi.simulateCallback({
        paymentNo: payment.paymentNo,
        callbackId: `web-detail-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
        paidAmount: payment.amount,
      });
      setMessage("模拟支付成功，订单已进入待发货状态");
      notifyWorkspaceBadgesChanged();
      await loadOrder(order.id);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "模拟支付失败");
    } finally {
      setBusy(false);
    }
  }

  async function cancel() {
    if (!order) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await orderApi.cancel(order.id);
      setMessage("订单已取消");
      notifyWorkspaceBadgesChanged();
      await loadOrder(order.id);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "取消订单失败");
    } finally {
      setBusy(false);
    }
  }

  async function confirmReceipt() {
    if (!order) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await orderApi.confirmReceipt(order.id);
      setMessage("已确认收货，订单完成");
      notifyWorkspaceBadgesChanged();
      await loadOrder(order.id);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "确认收货失败");
    } finally {
      setBusy(false);
    }
  }

  async function applyRefund() {
    if (!order) return;
    const reason = refundReason.trim();
    if (!reason) {
      setError("请填写退款原因");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await refundApi.apply(order.id, reason);
      setRefundReason("");
      setShowRefundForm(false);
      setMessage("退款申请已提交，请在退款中心查看审核状态");
      notifyWorkspaceBadgesChanged();
      await loadOrder(order.id);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "提交退款申请失败");
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <p className="empty-state">订单详情加载中…</p>;
  if (!order) return <section className="content-section"><Notice>{error ?? "订单不存在"}</Notice></section>;

  return (
    <section className="content-section order-detail-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow copyable-value">ORDER · {order.orderNo}<CopyButton value={order.orderNo} label="复制订单号" /></p>
          <h1>订单详情</h1>
          <p>创建于 {formatDateTime(order.createTime)}</p>
        </div>
        <Link className="button button--secondary" href="/orders">返回订单列表</Link>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="order-detail-layout">
        <div className="order-detail-main">
          <section className="orders-panel order-detail-panel">
            <div className="panel-heading"><h2>商品明细</h2><span className={`tag order-status order-status--${order.status}`}>{formatOrderStatus(order.status)}</span></div>
            <div className="order-item-list">
              {order.items?.map((item) => {
                const image = assetUrl(item.skuImageUrl || item.productImageUrl);
                return (
                  <div className="order-item-row" key={item.id}>
                    <div className="order-item-row__image"><MediaImage alt={item.productName} fallback={<span>C</span>} src={image} /></div>
                    <div className="order-item-row__info"><strong>{item.productName}</strong><span>{item.specificationValues?.map((value) => `${value.name}: ${value.value}`).join(" / ") || "默认规格"}</span><small>数量：{item.quantity}</small></div>
                    <strong>{formatMoney(item.totalAmount)}</strong>
                  </div>
                );
              })}
            </div>
            <div className="order-total-row"><span>订单应付</span><strong>{formatMoney(order.payableAmount)}</strong></div>
          </section>
          <section className="orders-panel order-detail-panel">
            <div className="panel-heading"><h2>收货地址快照</h2></div>
            <div className="snapshot-card"><strong>{order.receiverName || "—"}</strong><span>{order.receiverMobile || "—"}</span><p>{order.receiverAreaName ? `${order.receiverAreaName} · ` : ""}{order.receiverDetailAddress || "—"}</p></div>
          </section>
          {order.status >= 30 && order.status !== 50 ? (
            <section className="orders-panel order-detail-panel">
              <div className="panel-heading"><h2>物流信息</h2></div>
              <div className="snapshot-card"><strong>{order.logisticsCompany || "商家物流"}</strong><span className="copyable-value">运单号：{order.trackingNo || "—"}{order.trackingNo ? <CopyButton value={order.trackingNo} label="复制运单号" /> : null}</span><p>发货时间：{formatDateTime(order.shippingTime)}</p></div>
            </section>
          ) : null}
        </div>
        <aside className="order-detail-aside">
          <section className="orders-panel order-action-panel">
            <p className="eyebrow">PAYMENT CENTER</p>
            <h2>{formatOrderStatus(order.status)}</h2>
            <div className="summary-row"><span>支付状态</span><strong>{formatPaymentStatus(order.paymentStatus)}</strong></div>
            <div className="summary-row"><span>支付金额</span><strong>{formatMoney(order.paymentAmount ?? order.payableAmount)}</strong></div>
            {order.paymentNo ? <p className="payment-number copyable-value">支付单号：{order.paymentNo}<CopyButton value={order.paymentNo} label="复制" /></p> : null}
            {order.status === 10 ? <details className="order-test-tools"><summary><FlaskConical aria-hidden="true" size={16} />演示环境支付工具</summary><div><p>这里仅用于验收支付回调和订单状态流转，不代表真实支付入口。</p><button className="button button--primary button--full" disabled={busy} type="button" onClick={() => void pay()}>{busy ? "处理中…" : "模拟支付成功"}</button></div></details> : null}
            {order.status === 10 ? <button className="button button--secondary button--full" disabled={busy} type="button" onClick={() => setPendingAction("cancel")}>取消订单</button> : null}
            {order.status === 30 ? <button className="button button--primary button--full" disabled={busy} type="button" onClick={() => setPendingAction("receipt")}>{busy ? "处理中…" : "确认收货"}</button> : null}
            {order.refund ? (
              <div className="refund-status-box">
                <span>售后状态</span>
                <strong>{formatRefundStatus(order.refund.status)}</strong>
                <small className="copyable-value">退款单：{order.refund.refundNo}<CopyButton value={order.refund.refundNo} label="复制" /></small>
              </div>
            ) : null}
            {[20, 30, 40].includes(order.status) && !order.refund ? (
              <>
                {!showRefundForm ? <button className="button button--secondary button--full" disabled={busy} type="button" onClick={() => setShowRefundForm(true)}>申请退款</button> : null}
                {showRefundForm ? (
                  <div className="refund-form">
                    <label htmlFor="refund-reason">退款原因</label>
                    <textarea id="refund-reason" maxLength={255} onChange={(event) => setRefundReason(event.target.value)} placeholder="请说明申请退款的原因" rows={4} value={refundReason} />
                    <div className="inline-actions">
                      <button className="button button--primary" disabled={busy} type="button" onClick={() => void applyRefund()}>{busy ? "提交中…" : "提交申请"}</button>
                      <button className="button button--secondary" disabled={busy} type="button" onClick={() => setShowRefundForm(false)}>取消</button>
                    </div>
                  </div>
                ) : null}
              </>
            ) : null}
            {order.status === 40 ? <p className="action-complete">订单已完成，感谢你的购买。</p> : null}
            {order.status === 50 ? <p className="action-complete">订单已取消，库存已按规则处理。</p> : null}
          </section>
          <section className="orders-panel timeline-panel">
            <p className="eyebrow">ORDER TIMELINE</p>
            <div className="timeline-row timeline-row--done"><CheckCircle2 aria-hidden="true" size={17} /><span>创建订单</span><time>{formatDateTime(order.createTime)}</time></div>
            <div className={order.paidTime ? "timeline-row timeline-row--done" : "timeline-row"}>{order.paidTime ? <CheckCircle2 aria-hidden="true" size={17} /> : <Circle aria-hidden="true" size={17} />}<span>完成支付</span><time>{order.paidTime ? formatDateTime(order.paidTime) : "等待中"}</time></div>
            <div className={order.shippingTime ? "timeline-row timeline-row--done" : "timeline-row"}>{order.shippingTime ? <CheckCircle2 aria-hidden="true" size={17} /> : <Circle aria-hidden="true" size={17} />}<span>卖家发货</span><time>{order.shippingTime ? formatDateTime(order.shippingTime) : "等待中"}</time></div>
            <div className={order.completionTime ? "timeline-row timeline-row--done" : "timeline-row"}>{order.completionTime ? <CheckCircle2 aria-hidden="true" size={17} /> : <Circle aria-hidden="true" size={17} />}<span>确认收货</span><time>{order.completionTime ? formatDateTime(order.completionTime) : "等待中"}</time></div>
          </section>
        </aside>
      </div>
      <ConfirmDialog open={pendingAction !== null} dangerous={pendingAction === "cancel"} title={pendingAction === "receipt" ? "确认已经收到商品？" : "取消这笔订单？"} description={pendingAction === "receipt" ? "确认后订单将完成。如商品存在问题，请先申请售后。" : `订单 ${order.orderNo} 将被取消，库存会按交易规则恢复。`} confirmLabel={pendingAction === "receipt" ? "确认收货" : "确认取消"} busy={busy} onClose={() => setPendingAction(null)} onConfirm={() => { const action = pendingAction; setPendingAction(null); if (action === "receipt") void confirmReceipt(); else void cancel(); }} />
    </section>
  );
}
