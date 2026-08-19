import { appApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, RefundDetail, RefundSummary } from "@/lib/types/api";

export const refundApi = {
  apply(orderId: number, reason: string) {
    return appApi<RefundDetail>("/commerce/refund/apply", {
      method: "POST",
      body: jsonBody({ orderId, reason }),
    });
  },

  page(input: { pageNo: number; pageSize: number; status?: number; orderNo?: string }) {
    const params = new URLSearchParams({
      pageNo: String(input.pageNo),
      pageSize: String(input.pageSize),
    });
    if (input.status !== undefined) params.set("status", String(input.status));
    if (input.orderNo?.trim()) params.set("orderNo", input.orderNo.trim());
    return appApi<ApiPage<RefundSummary>>(`/commerce/refund/page?${params.toString()}`);
  },

  detail(id: number) {
    return appApi<RefundDetail>(`/commerce/refund/get?id=${id}`);
  },
};
