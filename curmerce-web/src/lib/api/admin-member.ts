import { adminApi } from "@/lib/api/client";
import type { AdminMemberSummary, ApiPage } from "@/lib/types/api";

export const adminMemberApi = {
  page(input: { pageNo: number; pageSize: number; keyword?: string; status?: number }) {
    const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
    if (input.keyword) params.set("keyword", input.keyword);
    if (input.status !== undefined) params.set("status", String(input.status));
    return adminApi<ApiPage<AdminMemberSummary>>(`/member/user/page?${params.toString()}`);
  },
  get(id: number) {
    return adminApi<AdminMemberSummary>(`/member/user/get?id=${id}`);
  },
};
