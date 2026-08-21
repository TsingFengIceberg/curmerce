import { appApi, jsonBody } from "@/lib/api/client";
import type {
  MemberAddress,
  MemberAddressInput,
  MemberAddressUpdateInput,
  AreaNode,
  MemberProfile,
  MemberToken,
} from "@/lib/types/api";

export const memberApi = {
  register(input: { mobile: string; password: string; nickname: string }) {
    return appApi<MemberToken>("/member/auth/register", {
      method: "POST",
      body: jsonBody(input),
    });
  },

  login(input: { mobile: string; password: string }) {
    return appApi<MemberToken>("/member/auth/login", {
      method: "POST",
      body: jsonBody(input),
    });
  },

  refreshToken(refreshToken: string) {
    return appApi<MemberToken>(`/member/auth/refresh-token?refreshToken=${encodeURIComponent(refreshToken)}`, {
      method: "POST",
    });
  },

  logout() {
    return appApi<boolean>("/member/auth/logout", { method: "POST" });
  },

  getProfile() {
    return appApi<MemberProfile>("/member/profile/get");
  },

  updateProfile(input: Partial<Pick<MemberProfile, "nickname" | "avatar" | "email" | "sex">>) {
    return appApi<boolean>("/member/profile/update", {
      method: "PUT",
      body: jsonBody(input),
    });
  },

  areaTree() {
    return appApi<AreaNode[]>("/system/area/tree");
  },

  listAddresses() {
    return appApi<MemberAddress[]>("/member/address/list");
  },

  getAddress(id: number) {
    return appApi<MemberAddress>(`/member/address/get?id=${id}`);
  },

  getDefaultAddress() {
    return appApi<MemberAddress | null>("/member/address/get-default");
  },

  createAddress(input: MemberAddressInput) {
    return appApi<number>("/member/address/create", {
      method: "POST",
      body: jsonBody(input),
    });
  },

  updateAddress(input: MemberAddressUpdateInput) {
    return appApi<boolean>("/member/address/update", {
      method: "PUT",
      body: jsonBody(input),
    });
  },

  deleteAddress(id: number) {
    return appApi<boolean>(`/member/address/delete?id=${id}`, { method: "DELETE" });
  },
};
