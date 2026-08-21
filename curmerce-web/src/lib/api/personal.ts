import { appApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, PersonalListing, PersonalListingInput, PersonalSellerOrder } from "@/lib/types/api";

function listingQuery(input: { pageNo: number; pageSize: number; auditStatus?: number; saleStatus?: number }) {
  const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
  if (input.auditStatus !== undefined) params.set("auditStatus", String(input.auditStatus));
  if (input.saleStatus !== undefined) params.set("saleStatus", String(input.saleStatus));
  return params.toString();
}

export const personalApi = {
  page(input: { pageNo: number; pageSize: number; auditStatus?: number; saleStatus?: number }) {
    return appApi<ApiPage<PersonalListing>>(`/commerce/personal-listing/page?${listingQuery(input)}`);
  },
  get(id: number) {
    return appApi<PersonalListing>(`/commerce/personal-listing/get?id=${id}`);
  },
  create(input: PersonalListingInput) {
    return appApi<number>("/commerce/personal-listing/create", { method: "POST", body: jsonBody(input) });
  },
  update(input: PersonalListingInput & { id: number }) {
    return appApi<boolean>("/commerce/personal-listing/update", { method: "PUT", body: jsonBody(input) });
  },
  submit(id: number) {
    return appApi<boolean>(`/commerce/personal-listing/submit?id=${id}`, { method: "PUT" });
  },
  list(id: number) {
    return appApi<boolean>(`/commerce/personal-listing/list?id=${id}`, { method: "PUT" });
  },
  delist(id: number) {
    return appApi<boolean>(`/commerce/personal-listing/delist?id=${id}`, { method: "PUT" });
  },
  pendingShipment(input: { pageNo: number; pageSize: number }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    return appApi<ApiPage<PersonalSellerOrder>>(`/commerce/personal-seller/order/page-pending-shipment?${params.toString()}`);
  },
  ship(input: { id: number; logisticsCompany: string; trackingNo: string }) {
    return appApi<boolean>("/commerce/personal-seller/order/ship", { method: "PUT", body: jsonBody(input) });
  },
};
