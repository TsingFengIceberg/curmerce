import type { MemberToken } from "@/lib/types/api";

const ACCESS_TOKEN_KEY = "curmerce.access-token";
const REFRESH_TOKEN_KEY = "curmerce.refresh-token";

function canUseStorage() {
  return typeof window !== "undefined";
}

export function getAccessToken() {
  return canUseStorage() ? window.localStorage.getItem(ACCESS_TOKEN_KEY) : null;
}

export function getRefreshToken() {
  return canUseStorage() ? window.localStorage.getItem(REFRESH_TOKEN_KEY) : null;
}

export function saveToken(token: MemberToken) {
  if (!canUseStorage()) return;
  window.localStorage.setItem(ACCESS_TOKEN_KEY, token.accessToken);
  window.localStorage.setItem(REFRESH_TOKEN_KEY, token.refreshToken);
}

export function clearToken() {
  if (!canUseStorage()) return;
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
}
