import { adminApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, MerchantOrder } from "@/lib/types/api";

export const adminOrderApi = {
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
