import { adminAuthApi } from "@/lib/api/admin-auth";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import type { AdminPermissionInfo } from "@/lib/types/api";

interface RouterLike {
  replace(path: string): void;
}

let permissionCache: { token: string; expiresAt: number; value: AdminPermissionInfo } | null = null;
let permissionRequest: Promise<AdminPermissionInfo> | null = null;

export function safeReturnTo(value: string | null | undefined, fallback: string) {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/login") || value.startsWith("/register") || value.startsWith("/merchant/login")) return fallback;
  return value;
}

export function loginPath(path: "/login" | "/merchant/login", returnTo?: string) {
  const safe = safeReturnTo(returnTo, "");
  return safe ? `${path}?returnTo=${encodeURIComponent(safe)}` : path;
}

export function currentLocation() {
  return typeof window === "undefined" ? undefined : `${window.location.pathname}${window.location.search}${window.location.hash}`;
}

export async function getPermissionInfoCached(force = false) {
  const token = getAdminAccessToken();
  if (!token) throw new CurmerceApiError("后台登录状态不存在", 401);
  if (!force && permissionCache?.token === token && permissionCache.expiresAt > Date.now()) return permissionCache.value;
  if (!permissionRequest) {
    permissionRequest = adminAuthApi.getPermissionInfo().then((value) => {
      permissionCache = { token, expiresAt: Date.now() + 30_000, value };
      return value;
    }).finally(() => { permissionRequest = null; });
  }
  return permissionRequest;
}

/** Merchant self-service pages require the approved merchant_owner role. */
export async function ensureMerchantOwner(router: RouterLike, returnTo = currentLocation()): Promise<boolean> {
  if (!getAdminAccessToken()) {
    router.replace(loginPath("/merchant/login", returnTo));
    return false;
  }
  try {
    const permission = await getPermissionInfoCached();
    if (permission.roles?.includes("merchant_owner")) return true;
    if (permission.roles?.includes("super_admin")) {
      router.replace("/admin/merchants");
      return false;
    }
  } catch (cause) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      permissionCache = null;
      router.replace(loginPath("/merchant/login", returnTo));
      return false;
    }
  }
  clearAdminToken();
  permissionCache = null;
  router.replace(loginPath("/merchant/login", returnTo));
  return false;
}

/** Platform administration pages require the super_admin role. */
export async function ensurePlatformAdmin(router: RouterLike, returnTo = currentLocation()): Promise<boolean> {
  if (!getAdminAccessToken()) {
    const base = loginPath("/merchant/login", returnTo);
    router.replace(`${base}${base.includes("?") ? "&" : "?"}role=admin`);
    return false;
  }
  try {
    const permission = await getPermissionInfoCached();
    if (permission.roles?.includes("super_admin")) return true;
    if (permission.roles?.includes("merchant_owner")) {
      router.replace("/merchant");
      return false;
    }
  } catch (cause) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      permissionCache = null;
      const base = loginPath("/merchant/login", returnTo);
      router.replace(`${base}${base.includes("?") ? "&" : "?"}role=admin`);
      return false;
    }
  }
  clearAdminToken();
  permissionCache = null;
  const base = loginPath("/merchant/login", returnTo);
  router.replace(`${base}${base.includes("?") ? "&" : "?"}role=admin`);
  return false;
}
