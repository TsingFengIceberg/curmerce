import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FeedbackCenter, notifyFeedback } from "@/components/feedback-center";
import { ProcessTimeline } from "@/components/process-timeline";
import { ScheduleFields } from "@/components/schedule-fields";
import { TableDensityControl } from "@/components/table-view-controls";

describe("interaction foundations", () => {
  it("announces global feedback and exposes its recovery action", async () => {
    const retry = vi.fn();
    render(<FeedbackCenter />);
    notifyFeedback({ tone: "error", title: "保存失败", description: "网络超时", duration: 0, actionLabel: "重试", onAction: retry });
    expect(await screen.findByRole("alert")).toHaveTextContent("保存失败");
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(retry).toHaveBeenCalledOnce();
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
  });

  it("renders lifecycle state and accessible pending text", () => {
    render(<ProcessTimeline steps={[{ id: "one", label: "已创建", state: "done", time: "10:00" }, { id: "two", label: "等待发货", state: "current" }, { id: "three", label: "确认收货", state: "pending" }]} />);
    expect(screen.getByRole("list", { name: "流程进度" })).toBeInTheDocument();
    expect(screen.getByText("等待中")).toBeInTheDocument();
  });

  it("provides schedule presets, duration context and conflict hints", () => {
    const onChange = vi.fn();
    render(<ScheduleFields idPrefix="test" startTime="2026-08-27T10:00" endTime="2026-08-27T12:00" windows={[{ id: 1, name: "重叠活动", startTime: "2026-08-27T11:00", endTime: "2026-08-27T13:00" }]} onChange={onChange} />);
    expect(screen.getByText(/持续 2 小时/)).toBeInTheDocument();
    expect(screen.getByText(/重叠活动/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "3 天" }));
    expect(onChange).toHaveBeenCalledOnce();
  });

  it("offers named table density controls", () => {
    const onChange = vi.fn();
    render(<TableDensityControl density="comfortable" onChange={onChange} />);
    expect(screen.getByRole("button", { name: "舒适表格密度" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "紧凑表格密度" }));
    expect(onChange).toHaveBeenCalledWith("compact");
  });
});
