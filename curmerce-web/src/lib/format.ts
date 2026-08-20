export function formatMoney(fen?: number | null) {
  return `¥${((fen ?? 0) / 100).toFixed(2)}`;
}

export function formatStock(stock?: number | null) {
  return stock && stock > 0 ? `库存 ${stock}` : "暂时缺货";
}

export const ORDER_STATUS_LABELS: Record<number, string> = {
  10: "待支付",
  20: "待发货",
  30: "已发货",
  40: "已完成",
  50: "已取消",
};

export const PAYMENT_STATUS_LABELS: Record<number, string> = {
  10: "待支付",
  20: "支付成功",
  30: "已取消",
};

export const REFUND_STATUS_LABELS: Record<number, string> = {
  0: "无售后",
  10: "退款申请中",
  20: "退款通过 / 处理中",
  30: "退款成功",
  40: "退款拒绝",
  50: "退款失败",
};

export function formatOrderStatus(status?: number | null) {
  return status ? ORDER_STATUS_LABELS[status] ?? `状态 ${status}` : "未知状态";
}

export function formatMerchantStatus(status?: number | null) {
  return ({ 0: "待审核", 1: "已通过", 2: "已拒绝" } as Record<number, string>)[status ?? -1] ?? `状态 ${status ?? "—"}`;
}

export function formatPaymentStatus(status?: number | null) {
  return status ? PAYMENT_STATUS_LABELS[status] ?? `状态 ${status}` : "未创建支付单";
}

export function formatRefundStatus(status?: number | null) {
  return status === null || status === undefined ? "无售后" : REFUND_STATUS_LABELS[status] ?? `状态 ${status}`;
}

export function formatDateTime(value?: string | number | null) {
  if (value === null || value === undefined || value === "") return "—";
  const date = typeof value === "number" ? new Date(value) : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
