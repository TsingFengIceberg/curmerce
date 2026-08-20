import { adminApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, MerchantOrder } from "@/lib/types/api";

export const adminOrderApi = {
  page(input: { pageNo: number; pageSize: number; status?: number; orderNo?: string; merchantId?: number; memberUserId?: number }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    for (const [key, value] of Object.entries(input)) {
      if (key !== "pageNo" && key !== "pageSize" && value !== undefined && value !== "") params.set(key, String(value));
    }
    return adminApi<ApiPage<MerchantOrder>>(`/commerce/order/page?${params.toString()}`);
  },

  pageOwnPendingShipment(input: { pageNo: number; pageSize: number }) {
    const params = new URLSearchParams({
      pageNo: String(input.pageNo),
      pageSize: String(input.pageSize),
    });
    return adminApi<ApiPage<MerchantOrder>>(`/commerce/order/page-own-pending-shipment?${params.toString()}`);
  },

  shipOwn(input: { id: number; logisticsCompany: string; trackingNo: string }) {
    return adminApi<boolean>("/commerce/order/ship-own", {
      method: "PUT",
      body: jsonBody(input),
    });
  },
};
