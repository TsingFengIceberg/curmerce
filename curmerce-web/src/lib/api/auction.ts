import { adminApi, appApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, AuctionBid, AuctionCreateInput, AuctionSession } from "@/lib/types/api";

export const auctionApi = {
  page(input: { pageNo: number; pageSize: number; keyword?: string }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    if (input.keyword?.trim()) params.set("keyword", input.keyword.trim());
    return appApi<ApiPage<AuctionSession>>(`/commerce/auction/page?${params}`);
  },
  get(id: number) { return appApi<AuctionSession>(`/commerce/auction/get?id=${id}`); },
  bidPage(input: { sessionId: number; pageNo: number; pageSize: number }) {
    const params = new URLSearchParams({ sessionId: String(input.sessionId), pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    return appApi<ApiPage<AuctionBid>>(`/commerce/auction/bid-page?${params.toString()}`);
  },
  bid(input: { sessionId: number; amount: number; idempotencyKey: string }) { return appApi<number>("/commerce/auction/bid", { method: "POST", body: jsonBody(input) }); },
  settle(input: { sessionId: number; addressId: number }) { return appApi<number>("/commerce/auction/settle", { method: "POST", body: jsonBody(input) }); },
};

export const adminAuctionApi = {
  page(input: { pageNo: number; pageSize: number; status?: number; name?: string }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    if (input.status !== undefined) params.set("status", String(input.status));
    if (input.name?.trim()) params.set("name", input.name.trim());
    return adminApi<ApiPage<AuctionSession>>(`/commerce/auction/page?${params}`);
  },
  get(id: number) { return adminApi<AuctionSession>(`/commerce/auction/get?id=${id}`); },
  create(input: AuctionCreateInput) {
    return adminApi<number>("/commerce/auction/create", {
      method: "POST",
      body: jsonBody({
        ...input,
        startTime: new Date(input.startTime).getTime(),
        endTime: new Date(input.endTime).getTime(),
      }),
    });
  },
  update(id: number, input: AuctionCreateInput) {
    return adminApi<boolean>("/commerce/auction/update", {
      method: "PUT",
      body: jsonBody({
        id,
        ...input,
        startTime: new Date(input.startTime).getTime(),
        endTime: new Date(input.endTime).getTime(),
      }),
    });
  },
  publish(id: number) { return adminApi<boolean>(`/commerce/auction/publish?id=${id}`, { method: "PUT" }); },
  cancel(id: number) { return adminApi<boolean>(`/commerce/auction/cancel?id=${id}`, { method: "PUT" }); },
  end(id: number) { return adminApi<boolean>(`/commerce/auction/end?id=${id}`, { method: "PUT" }); },
};
