import { adminApi, jsonBody } from "@/lib/api/client";
import type { AdminRefundPageQuery, ApiPage, RefundDetail } from "@/lib/types/api";

function pageQuery(input: AdminRefundPageQuery) {
  const params = new URLSearchParams({
    pageNo: String(input.pageNo),
    pageSize: String(input.pageSize),
  });
  if (input.status !== undefined) params.set("status", String(input.status));
  if (input.orderNo?.trim()) params.set("orderNo", input.orderNo.trim());
  if (input.memberUserId !== undefined) params.set("memberUserId", String(input.memberUserId));
  return params.toString();
}

export const adminRefundApi = {
  page(input: AdminRefundPageQuery) {
    return adminApi<ApiPage<RefundDetail>>(`/commerce/refund/page?${pageQuery(input)}`);
  },

  pageOwn(input: AdminRefundPageQuery) {
    return adminApi<ApiPage<RefundDetail>>(`/commerce/refund/page-own?${pageQuery(input)}`);
  },

  detail(id: number) {
    return adminApi<RefundDetail>(`/commerce/refund/get?id=${id}`);
  },

  detailOwn(id: number) {
    return adminApi<RefundDetail>(`/commerce/refund/get-own?id=${id}`);
  },

  approve(id: number, remark: string) {
    return adminApi<boolean>("/commerce/refund/approve", {
      method: "PUT",
      body: jsonBody({ id, remark: remark.trim() || undefined }),
    });
  },

  reject(id: number, remark: string) {
    return adminApi<boolean>("/commerce/refund/reject", {
      method: "PUT",
      body: jsonBody({ id, remark: remark.trim() }),
    });
  },

  approveOwn(id: number, remark: string) {
    return adminApi<boolean>("/commerce/refund/approve-own", {
      method: "PUT",
      body: jsonBody({ id, remark: remark.trim() || undefined }),
    });
  },

  rejectOwn(id: number, remark: string) {
    return adminApi<boolean>("/commerce/refund/reject-own", {
      method: "PUT",
      body: jsonBody({ id, remark: remark.trim() }),
    });
  },

  simulateCallback(input: { refundNo: string; callbackId: string; success: boolean }) {
    return adminApi<RefundDetail>("/commerce/refund/simulate-callback", {
      method: "POST",
      body: jsonBody(input),
    });
  },
};
