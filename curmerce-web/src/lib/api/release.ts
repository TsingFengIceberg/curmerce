import { adminApi, appApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, ReleaseCampaign, ReleaseCreateInput, ReleasePurchaseResult } from "@/lib/types/api";

export const releaseApi = {
  page(input: { pageNo: number; pageSize: number; keyword?: string }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    if (input.keyword?.trim()) params.set("keyword", input.keyword.trim());
    return appApi<ApiPage<ReleaseCampaign>>(`/commerce/release/page?${params}`);
  },
  get(id: number) { return appApi<ReleaseCampaign>(`/commerce/release/get?id=${id}`); },
  purchase(input: { itemId: number; quantity: number; addressId: number; idempotencyKey: string }) { return appApi<ReleasePurchaseResult>("/commerce/release/purchase", { method: "POST", body: jsonBody(input) }); },
};

export const adminReleaseApi = {
  page(input: { pageNo: number; pageSize: number; status?: number; name?: string }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    if (input.status !== undefined) params.set("status", String(input.status));
    if (input.name?.trim()) params.set("name", input.name.trim());
    return adminApi<ApiPage<ReleaseCampaign>>(`/commerce/release/page?${params}`);
  },
  get(id: number) { return adminApi<ReleaseCampaign>(`/commerce/release/get?id=${id}`); },
  create(input: ReleaseCreateInput) {
    return adminApi<number>("/commerce/release/create", {
      method: "POST",
      body: jsonBody({
        ...input,
        startTime: new Date(input.startTime).getTime(),
        endTime: new Date(input.endTime).getTime(),
      }),
    });
  },
  update(id: number, input: ReleaseCreateInput) {
    return adminApi<boolean>("/commerce/release/update", {
      method: "PUT",
      body: jsonBody({
        id,
        ...input,
        startTime: new Date(input.startTime).getTime(),
        endTime: new Date(input.endTime).getTime(),
      }),
    });
  },
  publish(id: number) { return adminApi<boolean>(`/commerce/release/publish?id=${id}`, { method: "PUT" }); },
  cancel(id: number) { return adminApi<boolean>(`/commerce/release/cancel?id=${id}`, { method: "PUT" }); },
  finish(id: number) { return adminApi<boolean>(`/commerce/release/finish?id=${id}`, { method: "PUT" }); },
};
