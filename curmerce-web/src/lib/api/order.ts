import { appApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, OrderCreateResult, OrderDetail, OrderSummary } from "@/lib/types/api";

export const orderApi = {
  create(addressId: number, idempotencyKey: string) {
    return appApi<OrderCreateResult>("/commerce/order/create", {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey,
      },
      body: jsonBody({ addressId }),
    });
  },

  page(input: { pageNo: number; pageSize: number; status?: number }) {
    const params = new URLSearchParams({
      pageNo: String(input.pageNo),
      pageSize: String(input.pageSize),
    });
    if (input.status) params.set("status", String(input.status));
    return appApi<ApiPage<OrderSummary>>(`/commerce/order/page?${params.toString()}`);
  },

  detail(id: number) {
    return appApi<OrderDetail>(`/commerce/order/get?id=${id}`);
  },

  cancel(id: number) {
    return appApi<boolean>("/commerce/order/cancel", {
      method: "PUT",
      body: jsonBody({ id }),
    });
  },

  confirmReceipt(id: number) {
    return appApi<boolean>("/commerce/order/confirm-receipt", {
      method: "PUT",
      body: jsonBody({ id }),
    });
  },
};
