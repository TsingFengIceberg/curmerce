import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MediaImage } from "@/components/media-image";

describe("MediaImage", () => {
  it("replaces a broken business image with an accessible fallback", () => {
    render(<MediaImage alt="演示商品" fallbackLabel="演示商品暂无图片" src="/broken-product.jpg" />);

    fireEvent.error(screen.getByRole("img", { name: "演示商品" }));

    expect(screen.getByRole("img", { name: "演示商品暂无图片" })).toBeVisible();
    expect(screen.queryByAltText("演示商品")).not.toBeInTheDocument();
  });
});
