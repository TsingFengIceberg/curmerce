import { appApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, ProductFavorite } from "@/lib/types/api";

export const productFavoriteApi = {
  set(productId: number, favorite: boolean) {
    return appApi<boolean>("/commerce/product-favorite/set", {
      method: "PUT",
      body: jsonBody({ productId, favorite }),
    });
  },

  status(productId: number) {
    return appApi<boolean>(`/commerce/product-favorite/status?productId=${productId}`);
  },

  page(pageNo: number, pageSize: number) {
    return appApi<ApiPage<ProductFavorite>>(`/commerce/product-favorite/page?pageNo=${pageNo}&pageSize=${pageSize}`);
  },
};
