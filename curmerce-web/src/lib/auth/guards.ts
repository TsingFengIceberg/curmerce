import { adminAuthApi } from "@/lib/api/admin-auth";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";

interface RouterLike {
  replace(path: string): void;
}

/** Merchant self-service pages require the approved merchant_owner role. */
export async function ensureMerchantOwner(router: RouterLike): Promise<boolean> {
  if (!getAdminAccessToken()) {
    router.replace("/merchant/login");
    return false;
  }
  try {
    const permission = await adminAuthApi.getPermissionInfo();
    if (permission.roles?.includes("merchant_owner")) return true;
    if (permission.roles?.includes("super_admin")) {
      router.replace("/admin/merchants");
      return false;
    }
  } catch (cause) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      router.replace("/merchant/login");
      return false;
    }
  }
  clearAdminToken();
  router.replace("/merchant/login");
  return false;
}
