import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MerchantAccessBoundary } from "@/components/merchant-access-boundary";

const { ensureMerchantOwner, replace, router, usePathname } = vi.hoisted(() => {
  const replace = vi.fn();
  return {
    ensureMerchantOwner: vi.fn(),
    replace,
    router: { replace },
    usePathname: vi.fn(),
  };
});

vi.mock("next/navigation", () => ({
  usePathname,
  useRouter: () => router,
}));

vi.mock("@/lib/auth/guards", () => ({
  ensureMerchantOwner,
}));

describe("MerchantAccessBoundary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    usePathname.mockReturnValue("/merchant/login");
    ensureMerchantOwner.mockResolvedValue(false);
  });

  it("renders the login page without checking merchant permissions", () => {
    render(<MerchantAccessBoundary><p>商家登录表单</p></MerchantAccessBoundary>);

    expect(screen.getByText("商家登录表单")).toBeInTheDocument();
    expect(ensureMerchantOwner).not.toHaveBeenCalled();
  });

  it("checks permissions for workspace routes", async () => {
    usePathname.mockReturnValue("/merchant/orders");
    ensureMerchantOwner.mockResolvedValue(true);

    render(<MerchantAccessBoundary><p>商家订单</p></MerchantAccessBoundary>);

    expect(screen.getByText("正在验证商家身份…")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("商家订单")).toBeInTheDocument());
    expect(ensureMerchantOwner).toHaveBeenCalledOnce();
  });
});
