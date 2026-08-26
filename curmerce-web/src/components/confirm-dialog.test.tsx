import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "@/components/confirm-dialog";

describe("ConfirmDialog", () => {
  it("binds its accessible description and invokes the requested action", () => {
    const onConfirm = vi.fn();
    render(<ConfirmDialog open title="删除商品？" description="删除后无法恢复。" confirmLabel="确认删除" dangerous onClose={vi.fn()} onConfirm={onConfirm} />);

    const dialog = screen.getByRole("dialog", { name: "删除商品？" });
    expect(dialog).toHaveAccessibleDescription("删除后无法恢复。");
    expect(dialog).toHaveAttribute("open");
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("disables every exit while a confirmation is busy", () => {
    render(<ConfirmDialog open title="正在处理" description="请稍候。" busy onClose={vi.fn()} onConfirm={vi.fn()} />);
    expect(screen.getByRole("button", { name: "关闭" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "返回" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "处理中…" })).toBeDisabled();
  });
});
