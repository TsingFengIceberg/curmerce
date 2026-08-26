"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { CurmerceApiError } from "@/lib/api/client";
import { refundApi } from "@/lib/api/refund";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatRefundStatus } from "@/lib/format";
import type { RefundSummary } from "@/lib/types/api";

const statusFilters = [
  { value: 0, label: "全部退款" },
  { value: 10, label: "申请中" },
  { value: 20, label: "处理中" },
  { value: 30, label: "已成功" },
  { value: 40, label: "已拒绝" },
  { value: 50, label: "已失败" },
];
const PAGE_SIZE = 12;

export default function RefundsPage() {
  const router = useRouter();
  const [refunds, setRefunds] = useState<RefundSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState(0);
  const [orderNo, setOrderNo] = useState("");
  const [query, setQuery] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void loadRefunds(status, query);
  }, [router, status, query, pageNo]);

  async function loadRefunds(nextStatus = status, nextOrderNo = query) {
    setLoading(true);
    setError(null);
    try {
      const response = await refundApi.page({
        pageNo,
        pageSize: PAGE_SIZE,
        status: nextStatus || undefined,
        orderNo: nextOrderNo || undefined,
      });
      setRefunds(response?.list ?? []);
      setTotal(response?.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "退款记录加载失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="content-section refunds-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">ACCOUNT · REFUNDS</p>
          <h1>退款中心</h1>
          <p>查看退款申请、平台审核和退款结果的处理进度。</p>
        </div>
        <Link className="button button--secondary" href="/orders">返回订单</Link>
      </div>
      {error ? <Notice>{error}</Notice> : null}
      <div className="refund-toolbar">
        <div className="order-tabs" role="tablist" aria-label="退款状态筛选">
          {statusFilters.map((item) => (
            <button className={`order-tab${status === item.value ? " order-tab--active" : ""}`} key={item.value} type="button" onClick={() => { setStatus(item.value); setPageNo(1); }}>{item.label}</button>
          ))}
        </div>
        <form className="refund-search" onSubmit={(event) => { event.preventDefault(); setPageNo(1); setQuery(orderNo.trim()); }}>
          <input aria-label="订单号" onChange={(event) => setOrderNo(event.target.value)} placeholder="按订单号查询" value={orderNo} />
          <button className="button button--secondary" type="submit">查询</button>
        </form>
      </div>
      <div className="orders-panel">
        <div className="panel-heading"><h2>退款记录</h2><span>{total} 条</span></div>
        {loading ? <p className="empty-state">退款记录加载中…</p> : null}
        {!loading && refunds.length === 0 ? <p className="empty-state">当前筛选下还没有退款记录。</p> : null}
        {!loading && refunds.length > 0 ? (
          <div className="refund-list">
            {refunds.map((refund) => (
              <Link className="refund-card" href={`/refunds/${refund.id}`} key={refund.id}>
                <div className="refund-card__topline"><strong>{refund.refundNo}</strong><span className={`tag refund-status refund-status--${refund.status}`}>{formatRefundStatus(refund.status)}</span></div>
                <div className="refund-card__meta"><span>订单：{refund.orderNo || refund.orderId || "—"}</span><span>申请时间：{formatDateTime(refund.requestedTime)}</span></div>
                <div className="refund-card__bottomline"><span>{refund.reason || "未填写原因"}</span><strong>{formatMoney(refund.amount)}</strong></div>
              </Link>
            ))}
          </div>
        ) : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>
    </section>
  );
}
