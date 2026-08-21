"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { adminRefundApi } from "@/lib/api/admin-refund";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatRefundStatus } from "@/lib/format";
import type { RefundDetail } from "@/lib/types/api";
import { ensureMerchantOwner } from "@/lib/auth/guards";

type RefundScope = "admin" | "merchant";

const statusFilters = [
  { value: 0, label: "全部" },
  { value: 10, label: "待审核" },
  { value: 20, label: "处理中" },
  { value: 30, label: "已成功" },
  { value: 40, label: "已拒绝" },
  { value: 50, label: "已失败" },
];

export function RefundWorkbench({ scope }: { scope: RefundScope }) {
  const router = useRouter();
  const own = scope === "merchant";
  const [refunds, setRefunds] = useState<RefundDetail[]>([]);
  const [selected, setSelected] = useState<RefundDetail | null>(null);
  const [status, setStatus] = useState(0);
  const [orderNo, setOrderNo] = useState("");
  const [queryOrderNo, setQueryOrderNo] = useState("");
  const [total, setTotal] = useState(0);
  const [remark, setRemark] = useState("");
  const [callbackId, setCallbackId] = useState("");
  const [callbackSuccess, setCallbackSuccess] = useState(true);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (own) {
      void ensureMerchantOwner(router).then((allowed) => {
        if (allowed) void loadRefunds(status, queryOrderNo);
      });
      return;
    }
    if (!getAdminAccessToken()) {
      router.replace("/merchant/login");
      return;
    }
    void loadRefunds(status, queryOrderNo);
  }, [router, status, queryOrderNo]);

  async function loadRefunds(nextStatus = status, nextOrderNo = queryOrderNo) {
    setLoading(true);
    setError(null);
    try {
      const query = { pageNo: 1, pageSize: 20, status: nextStatus || undefined, orderNo: nextOrderNo || undefined };
      const response = own ? await adminRefundApi.pageOwn(query) : await adminRefundApi.page(query);
      const list = response?.list ?? [];
      setRefunds(list);
      setTotal(response?.total ?? 0);
      if (selected) {
        const refreshed = list.find((item) => item.id === selected.id);
        if (refreshed) await loadDetail(refreshed.id);
      }
    } catch (cause) {
      handleError(cause, "退款记录加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function loadDetail(id: number) {
    try {
      const detail = own ? await adminRefundApi.detailOwn(id) : await adminRefundApi.detail(id);
      setSelected(detail);
      setRemark(detail.reviewRemark ?? "");
      setCallbackId(`web-refund-${detail.id}-${Date.now()}`);
    } catch (cause) {
      handleError(cause, "退款详情加载失败");
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
    if (action === "reject" && !remark.trim()) {
      setError("驳回退款时必须填写审核备注");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (own) {
        if (action === "approve") await adminRefundApi.approveOwn(selected.id, remark);
        else await adminRefundApi.rejectOwn(selected.id, remark);
      } else if (action === "approve") {
        await adminRefundApi.approve(selected.id, remark);
      } else {
        await adminRefundApi.reject(selected.id, remark);
      }
      setMessage(action === "approve" ? "退款已审核通过" : "退款已驳回");
      await loadRefunds();
      await loadDetail(selected.id);
    } catch (cause) {
      handleError(cause, "退款审核失败");
    } finally {
      setBusy(false);
    }
  }

  async function simulateCallback() {
    if (!selected) return;
    if (!callbackId.trim()) {
      setError("请填写回调幂等编号");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await adminRefundApi.simulateCallback({
        refundNo: selected.refundNo,
        callbackId: callbackId.trim(),
        success: callbackSuccess,
      });
      setMessage(callbackSuccess ? "退款成功回调已模拟" : "退款失败回调已模拟");
      await loadRefunds();
      await loadDetail(selected.id);
    } catch (cause) {
      handleError(cause, "模拟退款回调失败");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    clearAdminToken();
    router.replace("/merchant/login");
  }

  return (
    <section className="content-section admin-page refund-workbench-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{own ? "MERCHANT · AFTER-SALES" : "ADMIN · AFTER-SALES"}</p>
          <h1>{own ? "商家退款审核" : "平台退款审核"}</h1>
          <p>{own ? "只处理当前商家和店铺范围内的退款申请。" : "处理平台范围内的退款审核与模拟渠道回调。"}</p>
        </div>
        <div className="inline-actions">
          <Link className="button button--secondary" href={own ? "/merchant/products" : "/admin/categories"}>管理商品</Link>
          {!own ? <Link className="button button--secondary" href="/admin/product-review">商品审核</Link> : null}
          <button className="button button--secondary" type="button" onClick={() => void logout()}>退出后台</button>
        </div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="admin-toolbar">
        <div className="order-tabs" role="tablist" aria-label="退款状态筛选">
          {statusFilters.map((item) => (
            <button className={`order-tab${status === item.value ? " order-tab--active" : ""}`} key={item.value} type="button" onClick={() => setStatus(item.value)}>{item.label}</button>
          ))}
        </div>
        <form className="admin-search" onSubmit={(event) => { event.preventDefault(); setQueryOrderNo(orderNo.trim()); }}>
          <input aria-label="订单号" onChange={(event) => setOrderNo(event.target.value)} placeholder="订单号" value={orderNo} />
          <button className="button button--secondary" type="submit">查询</button>
        </form>
      </div>
      <div className="admin-split-layout">
        <div className="orders-panel">
          <div className="panel-heading"><h2>退款申请</h2><span>{total} 条</span></div>
          {loading ? <p className="empty-state">退款记录加载中…</p> : null}
          {!loading && refunds.length === 0 ? <p className="empty-state">当前筛选下没有退款记录。</p> : null}
          <div className="admin-record-list">
            {refunds.map((refund) => (
              <button className={`admin-record-card${selected?.id === refund.id ? " admin-record-card--active" : ""}`} key={refund.id} type="button" onClick={() => void loadDetail(refund.id)}>
                <div className="admin-record-card__top"><strong>{refund.refundNo}</strong><span className={`tag refund-status refund-status--${refund.status}`}>{formatRefundStatus(refund.status)}</span></div>
                <div className="admin-record-card__meta"><span>订单：{refund.orderNo}</span><span>{formatDateTime(refund.requestedTime)}</span></div>
                <div className="admin-record-card__bottom"><span>{refund.reason || "未填写原因"}</span><strong>{formatMoney(refund.amount)}</strong></div>
              </button>
            ))}
          </div>
        </div>
        <div className="orders-panel admin-detail-panel">
          {!selected ? <p className="empty-state">选择一条退款记录查看详情。</p> : (
            <>
              <div className="panel-heading"><h2>退款详情</h2><span className={`tag refund-status refund-status--${selected.status}`}>{formatRefundStatus(selected.status)}</span></div>
              <div className="detail-rows">
                <div><span>退款单号</span><strong>{selected.refundNo}</strong></div>
                <div><span>订单号</span><strong><Link className="text-button" href={`/orders/${selected.orderId}`}>{selected.orderNo}</Link></strong></div>
                <div><span>退款金额</span><strong>{formatMoney(selected.amount)}</strong></div>
                <div><span>申请时间</span><strong>{formatDateTime(selected.requestedTime)}</strong></div>
                <div><span>申请原因</span><strong>{selected.reason || "—"}</strong></div>
                <div><span>审核时间</span><strong>{formatDateTime(selected.reviewedTime)}</strong></div>
                <div><span>审核备注</span><strong>{selected.reviewRemark || "—"}</strong></div>
                <div><span>回调状态</span><strong>{selected.callbackSuccess === true ? "成功" : selected.callbackSuccess === false ? "失败" : "未回调"}</strong></div>
              </div>
              {selected.status === 10 ? (
                <div className="admin-action-box">
                  <label className="field"><span>审核备注</span><textarea maxLength={255} onChange={(event) => setRemark(event.target.value)} placeholder="通过可填写说明，驳回必须填写原因" rows={3} value={remark} /></label>
                  <div className="inline-actions"><button className="button button--primary" disabled={busy} type="button" onClick={() => void review("approve")}>审核通过</button><button className="button button--danger" disabled={busy} type="button" onClick={() => void review("reject")}>驳回退款</button></div>
                </div>
              ) : null}
              {selected.status === 20 ? (
                <div className="admin-action-box">
                  <label className="field"><span>回调幂等编号</span><input maxLength={64} onChange={(event) => setCallbackId(event.target.value)} value={callbackId} /></label>
                  <label className="field"><span>回调结果</span><select onChange={(event) => setCallbackSuccess(event.target.value === "success")} value={callbackSuccess ? "success" : "failure"}><option value="success">成功</option><option value="failure">失败</option></select></label>
                  <button className="button button--primary" disabled={busy} type="button" onClick={() => void simulateCallback()}>模拟退款回调</button>
                </div>
              ) : null}
            </>
          )}
        </div>
      </div>
    </section>
  );
}
