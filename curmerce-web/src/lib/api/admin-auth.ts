import { adminApi, jsonBody } from "@/lib/api/client";
import { clearAdminToken, saveAdminToken } from "@/lib/auth/storage";
import type { AdminPermissionInfo, MemberToken } from "@/lib/types/api";

export const adminAuthApi = {
  async login(input: { username: string; password: string }) {
    const token = await adminApi<MemberToken>("/system/auth/login", {
      method: "POST",
      body: jsonBody(input),
    });
    saveAdminToken(token);
    return token;
  },

  async logout() {
    try {
      await adminApi<boolean>("/system/auth/logout", { method: "POST" });
    } finally {
      clearAdminToken();
    }
  },

  getPermissionInfo() {
    return adminApi<AdminPermissionInfo>("/system/auth/get-permission-info");
  },
};
