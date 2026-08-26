import { adminApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, MerchantOrder } from "@/lib/types/api";

function appendDateRange(params: URLSearchParams, dateFrom?: string, dateTo?: string) {
  if (!dateFrom && !dateTo) return;
  params.set("createTime[0]", dateFrom ? `${dateFrom} 00:00:00` : "1970-01-01 00:00:00");
  params.set("createTime[1]", dateTo ? `${dateTo} 23:59:59` : "9999-12-31 23:59:59");
}

export const adminOrderApi = {
  page(input: { pageNo: number; pageSize: number; status?: number; orderNo?: string; merchantId?: number; memberUserId?: number; dateFrom?: string; dateTo?: string }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    for (const [key, value] of Object.entries(input)) {
      if (key !== "pageNo" && key !== "pageSize" && key !== "dateFrom" && key !== "dateTo" && value !== undefined && value !== "") params.set(key, String(value));
    }
    appendDateRange(params, input.dateFrom, input.dateTo);
    return adminApi<ApiPage<MerchantOrder>>(`/commerce/order/page?${params.toString()}`);
  },

  pageOwnPendingShipment(input: { pageNo: number; pageSize: number }) {
    const params = new URLSearchParams({
      pageNo: String(input.pageNo),
      pageSize: String(input.pageSize),
    });
    return adminApi<ApiPage<MerchantOrder>>(`/commerce/order/page-own-pending-shipment?${params.toString()}`);
  },

  pageOwn(input: { pageNo: number; pageSize: number; status?: number; orderNo?: string; dateFrom?: string; dateTo?: string }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    if (input.status !== undefined) params.set("status", String(input.status));
    if (input.orderNo?.trim()) params.set("orderNo", input.orderNo.trim());
    appendDateRange(params, input.dateFrom, input.dateTo);
    return adminApi<ApiPage<MerchantOrder>>(`/commerce/order/page-own?${params.toString()}`);
  },

  shipOwn(input: { id: number; logisticsCompany: string; trackingNo: string }) {
    return adminApi<boolean>("/commerce/order/ship-own", {
      method: "PUT",
      body: jsonBody(input),
    });
  },
};
