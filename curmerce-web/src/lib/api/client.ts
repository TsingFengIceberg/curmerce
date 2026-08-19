import { getAccessToken, getAdminAccessToken } from "@/lib/auth/storage";
import type { ApiErrorShape, CommonResult } from "@/lib/types/api";

export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:48080").replace(/\/$/, "");
const APP_API_PREFIX = "/app-api";
const ADMIN_API_PREFIX = "/admin-api";
const TENANT_ID = process.env.NEXT_PUBLIC_TENANT_ID ?? "1";

export class CurmerceApiError extends Error {
  readonly code?: number;
  readonly status: number;

  constructor(message: string, status: number, code?: number) {
    super(message);
    this.name = "CurmerceApiError";
    this.status = status;
    this.code = code;
  }
}

function toAbsoluteUrl(path: string) {
  if (/^https?:\/\//.test(path)) return path;
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

export function assetUrl(path?: string | null) {
  if (!path) return null;
  return /^https?:\/\//.test(path) ? path : `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return { msg: text };
  }
}

export async function appApi<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  headers.set("Content-Type", "application/json");
  headers.set("tenant-id", TENANT_ID);

  const accessToken = getAccessToken();
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  const response = await fetch(toAbsoluteUrl(`${APP_API_PREFIX}${path}`), {
    ...init,
    headers,
    cache: "no-store",
  });
  const payload = (await readJson(response)) as CommonResult<T> | ApiErrorShape | null;

  if (!response.ok) {
    const error = payload as ApiErrorShape | null;
    throw new CurmerceApiError(error?.msg || `请求失败（HTTP ${response.status}）`, response.status, error?.code);
  }

  if (!payload || typeof payload !== "object" || !("code" in payload)) {
    throw new CurmerceApiError("后端返回格式不正确", response.status);
  }

  const result = payload as CommonResult<T>;
  if (result.code !== 0) {
    throw new CurmerceApiError(result.msg || "请求失败", response.status, result.code);
  }
  return result.data;
}

/**
 * Management APIs intentionally use a separate token from buyer APIs. A buyer
 * token must never be silently reused to access merchant/admin data.
 */
export async function adminApi<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  headers.set("Content-Type", "application/json");
  headers.set("tenant-id", TENANT_ID);

  const accessToken = getAdminAccessToken();
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  const response = await fetch(toAbsoluteUrl(`${ADMIN_API_PREFIX}${path}`), {
    ...init,
    headers,
    cache: "no-store",
  });
  const payload = (await readJson(response)) as CommonResult<T> | ApiErrorShape | null;

  if (!response.ok) {
    const error = payload as ApiErrorShape | null;
    throw new CurmerceApiError(error?.msg || `请求失败（HTTP ${response.status}）`, response.status, error?.code);
  }

  if (!payload || typeof payload !== "object" || !("code" in payload)) {
    throw new CurmerceApiError("后端返回格式不正确", response.status);
  }

  const result = payload as CommonResult<T>;
  if (result.code !== 0) {
    throw new CurmerceApiError(result.msg || "请求失败", response.status, result.code);
  }
  return result.data;
}

export function jsonBody(value: unknown): BodyInit {
  return JSON.stringify(value);
}
