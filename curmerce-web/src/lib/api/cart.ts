import { appApi, jsonBody } from "@/lib/api/client";
import type { CartList } from "@/lib/types/api";

export const cartApi = {
  list() {
    return appApi<CartList>("/commerce/cart/list");
  },

  count() {
    return appApi<number>("/commerce/cart/count");
  },

  add(input: { skuId: number; quantity: number }) {
    return appApi<number>("/commerce/cart/add", {
      method: "POST",
      body: jsonBody(input),
    });
  },

  updateQuantity(input: { id: number; quantity: number }) {
    return appApi<boolean>("/commerce/cart/update-quantity", {
      method: "PUT",
      body: jsonBody(input),
    });
  },

  updateSelected(input: { ids: number[]; selected: boolean }) {
    return appApi<boolean>("/commerce/cart/update-selected", {
      method: "PUT",
      body: jsonBody(input),
    });
  },

  delete(ids: number[]) {
    const query = ids.map((id) => `ids=${encodeURIComponent(id)}`).join("&");
    return appApi<boolean>(`/commerce/cart/delete?${query}`, { method: "DELETE" });
  },
};
