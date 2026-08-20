import { adminApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, MerchantSummary } from "@/lib/types/api";

export const adminMerchantApi = {
  page(input: { pageNo: number; pageSize: number; status?: number; name?: string; code?: string }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    for (const [key, value] of Object.entries(input)) {
      if (key !== "pageNo" && key !== "pageSize" && value !== undefined && value !== "") params.set(key, String(value));
    }
    return adminApi<ApiPage<MerchantSummary>>(`/commerce/merchant/page?${params.toString()}`);
  },
  create(input: { name: string; code: string; contactName: string; contactMobile: string; defaultStoreName: string; defaultStoreCode: string }) {
    return adminApi<number>("/commerce/merchant/create", { method: "POST", body: jsonBody(input) });
  },
  approve(input: { id: number; username: string; nickname: string; password: string }) {
    return adminApi<boolean>("/commerce/merchant/approve", { method: "PUT", body: jsonBody(input) });
  },
  reject(input: { id: number; reason: string }) {
    return adminApi<boolean>("/commerce/merchant/reject", { method: "PUT", body: jsonBody(input) });
  },
};
