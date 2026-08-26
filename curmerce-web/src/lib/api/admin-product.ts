import { adminApi, jsonBody } from "@/lib/api/client";
import type {
  ApiPage,
  ProductAdmin,
  ProductCategoryNode,
  ProductOperationLog,
  ProductPageQuery,
  ProductSaveInput,
  StoreSummary,
} from "@/lib/types/api";

function productQuery(input: ProductPageQuery) {
  const params = new URLSearchParams({
    pageNo: String(input.pageNo),
    pageSize: String(input.pageSize),
  });
  const optional: Array<[string, string | number | undefined]> = [
    ["storeId", input.storeId],
    ["merchantId", input.merchantId],
    ["categoryId", input.categoryId],
    ["code", input.code?.trim()],
    ["name", input.name?.trim()],
    ["auditStatus", input.auditStatus],
    ["saleStatus", input.saleStatus],
  ];
  optional.forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  if (input.dateFrom || input.dateTo) {
    params.set("createTime[0]", input.dateFrom ? `${input.dateFrom} 00:00:00` : "1970-01-01 00:00:00");
    params.set("createTime[1]", input.dateTo ? `${input.dateTo} 23:59:59` : "9999-12-31 23:59:59");
  }
  return params.toString();
}

export const adminCategoryApi = {
  tree() {
    return adminApi<ProductCategoryNode[]>("/commerce/product-category/tree");
  },

  create(input: { parentId?: number; code: string; name: string; imageUrl: string; sort: number }) {
    return adminApi<number>("/commerce/product-category/create", { method: "POST", body: jsonBody(input) });
  },

  update(input: { id: number; parentId?: number; name: string; imageUrl: string; sort: number }) {
    return adminApi<boolean>("/commerce/product-category/update", { method: "PUT", body: jsonBody(input) });
  },

  updateStatus(input: { id: number; status: number }) {
    return adminApi<boolean>("/commerce/product-category/update-status", { method: "PUT", body: jsonBody(input) });
  },
};

export const adminStoreApi = {
  own() {
    return adminApi<StoreSummary>("/commerce/store/get-own");
  },

  updateOwn(input: { name: string; description: string; contactName: string; contactMobile: string }) {
    return adminApi<boolean>("/commerce/store/update-own", { method: "PUT", body: jsonBody(input) });
  },
};

export const adminProductApi = {
  pageOwn(input: ProductPageQuery) {
    return adminApi<ApiPage<ProductAdmin>>(`/commerce/product/page-own?${productQuery(input)}`);
  },

  detailOwn(id: number) {
    return adminApi<ProductAdmin>(`/commerce/product/get-own?id=${id}`);
  },

  createOwn(input: ProductSaveInput & { code: string }) {
    return adminApi<number>("/commerce/product/create-own", { method: "POST", body: jsonBody(input) });
  },

  updateOwn(input: ProductSaveInput & { id: number }) {
    return adminApi<boolean>("/commerce/product/update-own", { method: "PUT", body: jsonBody(input) });
  },

  submitOwn(id: number) {
    return adminApi<boolean>("/commerce/product/submit-own", { method: "PUT", body: jsonBody({ id }) });
  },

  listOwn(id: number) {
    return adminApi<boolean>("/commerce/product/list-own", { method: "PUT", body: jsonBody({ id }) });
  },

  delistOwn(id: number) {
    return adminApi<boolean>("/commerce/product/delist-own", { method: "PUT", body: jsonBody({ id }) });
  },

  operationLogOwn(productId: number, pageNo: number, pageSize: number) {
    const params = new URLSearchParams({ productId: String(productId), pageNo: String(pageNo), pageSize: String(pageSize) });
    return adminApi<ApiPage<ProductOperationLog>>(`/commerce/product/operation-log-own?${params.toString()}`);
  },

  reviewPage(input: ProductPageQuery) {
    return adminApi<ApiPage<ProductAdmin>>(`/commerce/product-review/page?${productQuery(input)}`);
  },

  reviewDetail(id: number) {
    return adminApi<ProductAdmin>(`/commerce/product-review/get?id=${id}`);
  },

  reviewOperationLog(productId: number, pageNo: number, pageSize: number) {
    const params = new URLSearchParams({ productId: String(productId), pageNo: String(pageNo), pageSize: String(pageSize) });
    return adminApi<ApiPage<ProductOperationLog>>(`/commerce/product-review/operation-log?${params.toString()}`);
  },

  approve(id: number) {
    return adminApi<boolean>("/commerce/product-review/approve", { method: "PUT", body: jsonBody({ id }) });
  },

  reject(id: number, reason: string) {
    return adminApi<boolean>("/commerce/product-review/reject", {
      method: "PUT",
      body: jsonBody({ id, reason: reason.trim() }),
    });
  },
};
