"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { refundApi } from "@/lib/api/refund";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatRefundStatus } from "@/lib/format";
import type { RefundDetail } from "@/lib/types/api";

export default function RefundDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [refund, setRefund] = useState<RefundDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    const id = Number(params.id);
    if (!Number.isInteger(id) || id < 1) {
      setError("退款编号不正确");
      setLoading(false);
      return;
    }
    void loadRefund(id);
  }, [params.id, router]);

  async function loadRefund(id: number) {
    setLoading(true);
    setError(null);
    try {
      setRefund(await refundApi.detail(id));
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "退款详情加载失败");
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <p className="empty-state">退款详情加载中…</p>;
  if (!refund) return <section className="content-section"><Notice>{error ?? "退款记录不存在"}</Notice></section>;

  return (
    <section className="content-section refund-detail-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">REFUND · {refund.refundNo}</p>
          <h1>退款详情</h1>
          <p>订单：{refund.orderNo}</p>
        </div>
        <div className="inline-actions"><Link className="button button--secondary" href={`/orders/${refund.orderId}`}>查看订单</Link><Link className="button button--secondary" href="/refunds">返回退款中心</Link></div>
      </div>
      {error ? <Notice>{error}</Notice> : null}
      <div className="refund-detail-grid">
        <section className="orders-panel">
          <div className="panel-heading"><h2>退款状态</h2><span className={`tag refund-status refund-status--${refund.status}`}>{formatRefundStatus(refund.status)}</span></div>
          <div className="detail-rows">
            <div><span>退款金额</span><strong>{formatMoney(refund.amount)}</strong></div>
            <div><span>申请时间</span><strong>{formatDateTime(refund.requestedTime)}</strong></div>
            <div><span>审核时间</span><strong>{formatDateTime(refund.reviewedTime)}</strong></div>
            <div><span>处理时间</span><strong>{formatDateTime(refund.processedTime)}</strong></div>
          </div>
        </section>
        <section className="orders-panel">
          <div className="panel-heading"><h2>申请信息</h2></div>
          <div className="snapshot-card"><strong>退款原因</strong><p>{refund.reason || "未填写"}</p>{refund.reviewRemark ? <><strong>审核备注</strong><p>{refund.reviewRemark}</p></> : null}</div>
          {refund.callbackId ? <p className="payment-number">回调编号：{refund.callbackId} · {refund.callbackSuccess ? "回调成功" : "回调未成功"}</p> : null}
        </section>
      </div>
    </section>
  );
}
