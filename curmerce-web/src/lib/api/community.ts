import { adminApi, appApi, appMultipartApi, jsonBody } from "@/lib/api/client";
import type { ApiPage, CommunityComment, CommunityPost, CommunityReport, CommunityTopic } from "@/lib/types/api";

function pageQuery(input: { pageNo: number; pageSize: number; keyword?: string; status?: number; topicSlug?: string; productId?: number }) {
  const params = new URLSearchParams({ pageNo: String(input.pageNo), pageSize: String(input.pageSize) });
  if (input.keyword?.trim()) params.set("keyword", input.keyword.trim());
  if (input.status !== undefined) params.set("status", String(input.status));
  if (input.topicSlug?.trim()) params.set("topicSlug", input.topicSlug.trim());
  if (input.productId) params.set("productId", String(input.productId));
  return params.toString();
}

export const communityApi = {
  page(input: { pageNo: number; pageSize: number; keyword?: string; topicSlug?: string; productId?: number }) {
    return appApi<ApiPage<CommunityPost>>(`/community/post/page?${pageQuery(input)}`);
  },
  popularTopics(limit = 12) {
    return appApi<CommunityTopic[]>(`/community/post/popular-topics?limit=${limit}`);
  },
  myPage(input: { pageNo: number; pageSize: number; keyword?: string }) {
    return appApi<ApiPage<CommunityPost>>(`/community/post/my-page?${pageQuery(input)}`);
  },
  favorites(input: { pageNo: number; pageSize: number; keyword?: string }) {
    return appApi<ApiPage<CommunityPost>>(`/community/post/favorites?${pageQuery(input)}`);
  },
  following(input: { pageNo: number; pageSize: number; keyword?: string }) {
    return appApi<ApiPage<CommunityPost>>(`/community/post/following?${pageQuery(input)}`);
  },
  get(id: number) { return appApi<CommunityPost>(`/community/post/get?id=${id}`); },
  create(input: { title: string; content: string; mediaUrls: string[]; topics: string[]; productIds: number[] }) {
    return appApi<number>("/community/post/create", { method: "POST", body: jsonBody(input) });
  },
  update(input: { id: number; title: string; content: string; mediaUrls: string[]; topics: string[]; productIds: number[] }) {
    return appApi<boolean>("/community/post/update", { method: "PUT", body: jsonBody(input) });
  },
  submit(id: number) { return appApi<boolean>(`/community/post/submit?id=${id}`, { method: "PUT" }); },
  pageComments(postId: number, pageNo = 1, pageSize = 50) {
    return appApi<ApiPage<CommunityComment>>(`/community/comment/page?postId=${postId}&pageNo=${pageNo}&pageSize=${pageSize}`);
  },
  comment(input: { postId: number; parentId?: number; content: string }) {
    return appApi<number>("/community/comment/create", { method: "POST", body: jsonBody(input) });
  },
  reaction(input: { postId: number; type: number; active: boolean }) {
    return appApi<boolean>("/community/post/reaction", { method: "PUT", body: jsonBody(input) });
  },
  follow(input: { userId: number; active: boolean }) {
    return appApi<boolean>("/community/follow", { method: "PUT", body: jsonBody(input) });
  },
  report(input: { postId: number; reason: string }) {
    return appApi<number>("/community/report/create", { method: "POST", body: jsonBody(input) });
  },
  uploadMedia(file: File) {
    const form = new FormData();
    form.append("file", file);
    form.append("directory", "community");
    return appMultipartApi<string>("/infra/file/upload", form);
  },
};

export const adminCommunityApi = {
  posts(input: { pageNo: number; pageSize: number; keyword?: string; status?: number }) {
    return adminApi<ApiPage<CommunityPost>>(`/community/post/page?${pageQuery(input)}`);
  },
  postStatus(input: { id: number; status: number }) {
    return adminApi<boolean>("/community/post/status", { method: "PUT", body: jsonBody(input) });
  },
  reports(input: { pageNo: number; pageSize: number; status?: number }) {
    return adminApi<ApiPage<CommunityReport>>(`/community/report/page?${pageQuery(input)}`);
  },
  reviewReport(input: { id: number; status: number; remark?: string }) {
    return adminApi<boolean>("/community/report/review", { method: "PUT", body: jsonBody(input) });
  },
};
