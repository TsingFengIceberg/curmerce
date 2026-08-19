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

  productPage(input: { pageNo: number; pageSize: number; categoryId?: number; keyword?: string }) {
    const params = new URLSearchParams({
      pageNo: String(input.pageNo),
      pageSize: String(input.pageSize),
    });
    if (input.categoryId) params.set("categoryId", String(input.categoryId));
    if (input.keyword?.trim()) params.set("keyword", input.keyword.trim());
    return appApi<ApiPage<PublicProductSummary>>(`/commerce/catalog/product-page?${params.toString()}`);
  },

  productDetail(id: number) {
    return appApi<PublicProductDetail>(`/commerce/catalog/product-detail?id=${id}`);
  },
};
