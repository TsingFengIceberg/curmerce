import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useUnsavedClose } from "@/hooks/use-unsaved-close";

function Harness({ dirty, onDiscard }: { dirty: boolean; onDiscard: () => void }) {
  const unsaved = useUnsavedClose({ dirty, onDiscard, subject: "活动草稿" });
  return <><button type="button" onClick={unsaved.requestClose}>关闭编辑</button>{unsaved.confirmation}</>;
}

describe("useUnsavedClose", () => {
  it("asks before discarding a changed form", () => {
    const onDiscard = vi.fn();
    render(<Harness dirty onDiscard={onDiscard} />);

    fireEvent.click(screen.getByRole("button", { name: "关闭编辑" }));
    expect(screen.getByRole("dialog", { name: "放弃活动草稿？" })).toBeVisible();
    expect(onDiscard).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "放弃修改" }));
    expect(onDiscard).toHaveBeenCalledOnce();
  });

  it("closes an unchanged form immediately", () => {
    const onDiscard = vi.fn();
    render(<Harness dirty={false} onDiscard={onDiscard} />);
    fireEvent.click(screen.getByRole("button", { name: "关闭编辑" }));
    expect(onDiscard).toHaveBeenCalledOnce();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
