import { appApi } from "@/lib/api/client";
import type {
  ApiPage,
  PublicCategoryNode,
  PublicProductDetail,
  PublicProductSummary,
} from "@/lib/types/api";

export const catalogApi = {
  categoryTree() {
    return appApi<PublicCategoryNode[]>("/commerce/catalog/category-tree");
  },

  productPage(input: { pageNo: number; pageSize: number; categoryId?: number; keyword?: string; minPrice?: number; maxPrice?: number; inStock?: boolean; sellerType?: number; storeKeyword?: string; sort?: "latest" | "priceAsc" | "priceDesc" }) {
    const params = new URLSearchParams({
      pageNo: String(input.pageNo),
      pageSize: String(input.pageSize),
    });
    if (input.categoryId) params.set("categoryId", String(input.categoryId));
    if (input.keyword?.trim()) params.set("keyword", input.keyword.trim());
    if (input.minPrice !== undefined) params.set("minPrice", String(input.minPrice));
    if (input.maxPrice !== undefined) params.set("maxPrice", String(input.maxPrice));
    if (input.inStock) params.set("inStock", "true");
    if (input.sellerType) params.set("sellerType", String(input.sellerType));
    if (input.storeKeyword?.trim()) params.set("storeKeyword", input.storeKeyword.trim());
    if (input.sort) params.set("sort", input.sort);
    return appApi<ApiPage<PublicProductSummary>>(`/commerce/catalog/product-page?${params.toString()}`);
  },

  productDetail(id: number) {
    return appApi<PublicProductDetail>(`/commerce/catalog/product-detail?id=${id}`);
  },
};
