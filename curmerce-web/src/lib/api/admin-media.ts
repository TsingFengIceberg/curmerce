import { adminApi, adminBlobApi, jsonBody } from "@/lib/api/client";
import type { ApiDateValue, ApiPage } from "@/lib/types/api";

export interface MediaAsset {
  id: number;
  assetKey: string;
  name?: string | null;
  path: string;
  type: string;
  size: number;
  sha256?: string | null;
  assetStatus: number;
  scanStatus: number;
  moderationStatus: number;
  moderationReason?: string | null;
  failureReason?: string | null;
  visibility: number;
  ownerUserId?: number | null;
  ownerUserType?: number | null;
  width?: number | null;
  height?: number | null;
  boundOnce?: boolean;
  orphanedAt?: ApiDateValue;
  createTime?: ApiDateValue;
}

export interface MediaPageQuery {
  pageNo: number;
  pageSize: number;
  assetKey?: string;
  assetStatus?: number;
  moderationStatus?: number;
  ownerUserId?: number;
}

function queryString(query: MediaPageQuery) {
  const values = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") values.set(key, String(value));
  });
  values.set("originalOnly", "true");
  return values.toString();
}

export const adminMediaApi = {
  page: (query: MediaPageQuery) => adminApi<ApiPage<MediaAsset>>(`/infra/media/page?${queryString(query)}`),
  quarantine: (id: number, reason?: string) => adminApi<boolean>(`/infra/media/${id}/quarantine`, { method: "POST", body: jsonBody({ reason }) }),
  release: (id: number, reason?: string) => adminApi<boolean>(`/infra/media/${id}/release`, { method: "POST", body: jsonBody({ reason }) }),
  reject: (id: number, reason?: string) => adminApi<boolean>(`/infra/media/${id}/reject`, { method: "POST", body: jsonBody({ reason }) }),
  retry: (id: number) => adminApi<boolean>(`/infra/media/${id}/retry`, { method: "POST" }),
  preview: (id: number, variant = "thumb-webp") => adminBlobApi(`/infra/media/${id}/content?variant=${encodeURIComponent(variant)}`),
};
