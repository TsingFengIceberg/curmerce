"use client";

import { History } from "lucide-react";
import { useEffect, useState } from "react";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { adminProductApi } from "@/lib/api/admin-product";
import { CurmerceApiError } from "@/lib/api/client";
import { personalApi } from "@/lib/api/personal";
import { formatDateTime } from "@/lib/format";
import type { ProductOperationLog } from "@/lib/types/api";
import { ProcessTimeline } from "@/components/process-timeline";

const PAGE_SIZE = 10;
const actorLabels: Record<number, string> = { 1: "个人卖家", 2: "商家", 3: "平台管理员" };
const actionLabels: Record<string, string> = {
  CREATE: "创建草稿",
  UPDATE: "修改商品",
  SUBMIT_REVIEW: "提交审核",
  APPROVE: "审核通过",
  REJECT: "审核驳回",
  LIST: "商品上架",
  DELIST: "商品下架",
};
const auditLabels: Record<number, string> = { 0: "草稿", 1: "待审核", 2: "审核通过", 3: "已驳回" };
const saleLabels: Record<number, string> = { 0: "下架", 1: "上架" };

type HistoryScope = "merchant" | "personal" | "admin";

export function ProductOperationHistory({ open, productId, productName, scope, onClose }: {
  open: boolean;
  productId?: number;
  productName?: string;
  scope: HistoryScope;
  onClose: () => void;
}) {
  const [logs, setLogs] = useState<ProductOperationLog[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setPageNo(1);
  }, [productId, scope]);

  useEffect(() => {
    if (!open || !productId) return;
    let active = true;
    setLoading(true);
    setError(null);
    const request = scope === "merchant"
      ? adminProductApi.operationLogOwn(productId, pageNo, PAGE_SIZE)
      : scope === "admin"
        ? adminProductApi.reviewOperationLog(productId, pageNo, PAGE_SIZE)
        : personalApi.operationLog(productId, pageNo, PAGE_SIZE);
    void request.then((page) => {
      if (!active) return;
      setLogs(page.list ?? []);
      setTotal(page.total ?? 0);
    }).catch((cause) => {
      if (!active) return;
      setLogs([]);
      setTotal(0);
      setError(cause instanceof CurmerceApiError ? cause.message : "商品操作记录加载失败");
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [open, pageNo, productId, scope]);

  return (
    <Drawer open={open} title="商品操作记录" description={productName ? `${productName} · 最近操作优先` : "最近操作优先"} onClose={onClose}>
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
      {!loading && !error && !logs.length ? <EmptyState icon={<History aria-hidden="true" size={23} />} title="暂无操作记录" description="创建、修改、审核和上下架操作将在这里留痕。" /> : null}
      {!loading && logs.length ? <ProcessTimeline label="商品操作记录" steps={logs.map((log) => ({ id: String(log.id), label: actionLabels[log.action] ?? log.action, time: formatDateTime(log.createTime), state: log.action === "REJECT" ? "error" as const : "done" as const, description: <span>{actorLabels[log.operatorType] ?? "系统角色"}{log.operatorUserId ? ` #${log.operatorUserId}` : ""}{transitionText(log)}{log.remark ? ` · ${log.remark}` : ""}</span> }))} /> : null}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
    </Drawer>
  );
}

function transitionText(log: ProductOperationLog) {
  const transitions: string[] = [];
  if (log.toAuditStatus != null && log.fromAuditStatus !== log.toAuditStatus) transitions.push(`审核：${log.fromAuditStatus == null ? "—" : auditLabels[log.fromAuditStatus] ?? log.fromAuditStatus} → ${auditLabels[log.toAuditStatus] ?? log.toAuditStatus}`);
  if (log.toSaleStatus != null && log.fromSaleStatus !== log.toSaleStatus) transitions.push(`销售：${log.fromSaleStatus == null ? "—" : saleLabels[log.fromSaleStatus] ?? log.fromSaleStatus} → ${saleLabels[log.toSaleStatus] ?? log.toSaleStatus}`);
  return transitions.length ? ` · ${transitions.join("；")}` : "";
}
