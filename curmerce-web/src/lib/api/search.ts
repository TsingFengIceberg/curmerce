import { appApi } from "@/lib/api/client";
import type { SearchPage, SearchPostDocument, SearchProductDocument } from "@/lib/types/api";

function query(input: { keyword: string; pageNo: number; pageSize: number }) {
  const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
  if (input.keyword.trim()) params.set("keyword", input.keyword.trim());
  return params.toString();
}

export const searchApi = {
  products(input: { keyword: string; pageNo: number; pageSize: number }) {
    return appApi<SearchPage<SearchProductDocument>>(`/search/products?${query(input)}`);
  },
  posts(input: { keyword: string; pageNo: number; pageSize: number }) {
    return appApi<SearchPage<SearchPostDocument>>(`/search/posts?${query(input)}`);
  },
};
