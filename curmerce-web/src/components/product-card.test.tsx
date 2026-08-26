import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProductCard } from "@/components/product-card";

describe("ProductCard", () => {
  it("uses the entire card as a single product link", () => {
    render(<ProductCard product={{ id: 18, categoryId: 2, storeId: 3, storeName: "山屿商店", name: "手作玻璃杯", minPrice: 12900, totalStock: 6, available: true }} />);

    const link = screen.getByRole("link", { name: "查看商品 手作玻璃杯" });
    expect(link).toHaveAttribute("href", "/products/18");
    expect(link).toContainElement(screen.getByText("手作玻璃杯"));
    expect(link).toContainElement(screen.getByText("¥129.00"));
  });
});
