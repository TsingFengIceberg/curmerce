import { describe, expect, it } from "vitest";
import { loginPath, safeReturnTo } from "@/lib/auth/guards";

describe("authentication return paths", () => {
  it("preserves safe in-application destinations", () => {
    expect(safeReturnTo("/checkout?source=cart#address", "/catalog")).toBe("/checkout?source=cart#address");
    expect(loginPath("/login", "/products/42")).toBe("/login?returnTo=%2Fproducts%2F42");
  });

  it("rejects external and recursive login destinations", () => {
    expect(safeReturnTo("//malicious.example", "/catalog")).toBe("/catalog");
    expect(safeReturnTo("https://malicious.example", "/catalog")).toBe("/catalog");
    expect(safeReturnTo("/merchant/login?returnTo=/admin", "/admin")).toBe("/admin");
  });
});
