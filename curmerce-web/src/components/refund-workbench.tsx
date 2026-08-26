"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { CopyButton } from "@/components/copy-button";
import { EmptyState } from "@/components/empty-state";
import { Pagination } from "@/components/pagination";
import { notifyWorkspaceBadgesChanged } from "@/components/workspace-shell";
import { RotateCcw } from "lucide-react";
import { CurmerceApiError } from "@/lib/api/client";
import { adminRefundApi } from "@/lib/api/admin-refund";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatRefundStatus } from "@/lib/format";
import type { RefundDetail } from "@/lib/types/api";
import { ensureMerchantOwner } from "@/lib/auth/guards";
import { positiveInt, useUrlQuery } from "@/hooks/use-url-query";

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
  const { searchParams, update } = useUrlQuery();
  const own = scope === "merchant";
  const [refunds, setRefunds] = useState<RefundDetail[]>([]);
  const [selected, setSelected] = useState<RefundDetail | null>(null);
  const status = Number(searchParams.get("status") ?? 0);
  const queryOrderNo = searchParams.get("orderNo") ?? "";
  const [orderNo, setOrderNo] = useState(queryOrderNo);
  const [total, setTotal] = useState(0);
  const pageNo = positiveInt(searchParams.get("page"));
  const [remark, setRemark] = useState("");
  const [callbackId, setCallbackId] = useState("");
  const [callbackSuccess, setCallbackSuccess] = useState(true);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [pendingReview, setPendingReview] = useState<"approve" | "reject" | null>(null);
  const testToolsEnabled = process.env.NODE_ENV !== "production";

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
  }, [router, status, queryOrderNo, pageNo]);

  useEffect(() => setOrderNo(queryOrderNo), [queryOrderNo]);

  async function loadRefunds(nextStatus = status, nextOrderNo = queryOrderNo) {
    setLoading(true);
    setError(null);
    try {
      const query = { pageNo, pageSize: 20, status: nextStatus || undefined, orderNo: nextOrderNo || undefined };
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
      setPendingReview(null);
      notifyWorkspaceBadgesChanged();
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
      notifyWorkspaceBadgesChanged();
      await loadRefunds();
      await loadDetail(selected.id);
    } catch (cause) {
      handleError(cause, "模拟退款回调失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="content-section admin-page refund-workbench-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{own ? "MERCHANT · AFTER-SALES" : "ADMIN · AFTER-SALES"}</p>
          <h1>{own ? "商家退款审核" : "平台退款审核"}</h1>
          <p>{own ? "优先处理待审核申请，并结合订单信息及时给买家反馈。" : "处理平台范围内的退款审核，并跟踪渠道退款结果。"}</p>
        </div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="admin-toolbar">
        <div className="order-tabs" role="tablist" aria-label="退款状态筛选">
          {statusFilters.map((item) => (
            <button className={`order-tab${status === item.value ? " order-tab--active" : ""}`} key={item.value} type="button" onClick={() => update({ status: item.value || null, page: 1 })}>{item.label}</button>
          ))}
        </div>
        <form className="admin-search" onSubmit={(event) => { event.preventDefault(); update({ page: 1, orderNo: orderNo.trim() }); }}>
          <input aria-label="订单号" onChange={(event) => setOrderNo(event.target.value)} placeholder="订单号" value={orderNo} />
          <button className="button button--secondary" type="submit">查询</button>
        </form>
      </div>
      <div className="admin-split-layout">
        <div className="orders-panel">
          <div className="panel-heading"><h2>退款申请</h2><span>{total} 条</span></div>
          {loading ? <p className="empty-state">退款记录加载中…</p> : null}
          {!loading && refunds.length === 0 ? <EmptyState icon={<RotateCcw aria-hidden="true" size={22} />} title="当前没有退款申请" description="调整状态或订单号后重新查询。" /> : null}
          <div className="admin-record-list">
            {refunds.map((refund) => (
              <button className={`admin-record-card${selected?.id === refund.id ? " admin-record-card--active" : ""}`} key={refund.id} type="button" onClick={() => void loadDetail(refund.id)}>
                <div className="admin-record-card__top"><strong>{refund.refundNo}</strong><span className={`tag refund-status refund-status--${refund.status}`}>{formatRefundStatus(refund.status)}</span></div>
                <div className="admin-record-card__meta"><span>订单：{refund.orderNo}</span><span>{formatDateTime(refund.requestedTime)}</span></div>
                <div className="admin-record-card__bottom"><span>{refund.reason || "未填写原因"}</span><strong>{formatMoney(refund.amount)}</strong></div>
              </button>
            ))}
          </div>
          <Pagination pageNo={pageNo} pageSize={20} total={total} onChange={(page) => update({ page })} />
        </div>
        <div className="orders-panel admin-detail-panel">
          {!selected ? <p className="empty-state">选择一条退款记录查看详情。</p> : (
            <>
              <div className="panel-heading"><h2>退款详情</h2><span className={`tag refund-status refund-status--${selected.status}`}>{formatRefundStatus(selected.status)}</span></div>
              <div className="detail-rows">
                <div><span>退款单号</span><strong className="copyable-value">{selected.refundNo}<CopyButton value={selected.refundNo} /></strong></div>
                <div><span>订单号</span><strong className="copyable-value">{selected.orderNo}<CopyButton value={selected.orderNo} /></strong></div>
                <div><span>退款金额</span><strong>{formatMoney(selected.amount)}</strong></div>
                <div><span>申请时间</span><strong>{formatDateTime(selected.requestedTime)}</strong></div>
                <div><span>申请原因</span><strong>{selected.reason || "—"}</strong></div>
                {selected.status === 10 ? <div><span>建议处理时限</span><strong>{selected.requestedTime ? `${formatDateTime(new Date(new Date(selected.requestedTime).getTime() + 48 * 60 * 60 * 1000).getTime())} 前` : "尽快处理"}</strong></div> : null}
                <div><span>审核时间</span><strong>{formatDateTime(selected.reviewedTime)}</strong></div>
                <div><span>审核备注</span><strong>{selected.reviewRemark || "—"}</strong></div>
                <div><span>回调状态</span><strong>{selected.callbackSuccess === true ? "成功" : selected.callbackSuccess === false ? "失败" : "未回调"}</strong></div>
              </div>
              {selected.status === 10 ? (
                <div className="admin-action-box">
                  <label className="field"><span>审核备注</span><textarea maxLength={255} onChange={(event) => setRemark(event.target.value)} placeholder="通过可填写说明，驳回必须填写原因" rows={3} value={remark} /></label>
                  <div className="inline-actions"><button className="button button--primary" disabled={busy} type="button" onClick={() => setPendingReview("approve")}>审核通过</button><button className="button button--danger" disabled={busy} type="button" onClick={() => setPendingReview("reject")}>驳回退款</button></div>
                </div>
              ) : null}
              {selected.status === 20 ? own ? (
                <p className="field-help">退款审核通过，平台正在继续处理退款结果。</p>
              ) : testToolsEnabled ? (
                <details className="order-test-tools"><summary>开发测试工具</summary><div className="admin-action-box">
                  <label className="field"><span>回调幂等编号</span><input maxLength={64} onChange={(event) => setCallbackId(event.target.value)} value={callbackId} /></label>
                  <label className="field"><span>回调结果</span><select onChange={(event) => setCallbackSuccess(event.target.value === "success")} value={callbackSuccess ? "success" : "failure"}><option value="success">成功</option><option value="failure">失败</option></select></label>
                  <button className="button button--primary" disabled={busy} type="button" onClick={() => void simulateCallback()}>模拟退款回调</button>
                </div></details>
              ) : <p className="field-help">退款正在等待渠道处理结果。</p> : null}
            </>
          )}
        </div>
      </div>
      <ConfirmDialog open={Boolean(pendingReview)} title={pendingReview === "approve" ? "通过退款申请" : "驳回退款申请"} description={pendingReview === "approve" ? `确认通过退款单 ${selected?.refundNo ?? ""}，金额 ${formatMoney(selected?.amount)}？` : `确认驳回退款单 ${selected?.refundNo ?? ""}？驳回原因会展示给买家。`} confirmLabel={pendingReview === "approve" ? "确认通过" : "确认驳回"} dangerous={pendingReview === "reject"} busy={busy} onClose={() => setPendingReview(null)} onConfirm={() => pendingReview && void review(pendingReview)} />
    </section>
  );
}
