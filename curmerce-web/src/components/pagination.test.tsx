import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Pagination } from "@/components/pagination";

describe("Pagination", () => {
  it("stays hidden when all records fit on one page", () => {
    const { container } = render(<Pagination pageNo={1} pageSize={20} total={20} onChange={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows boundary and neighboring pages and emits navigation", () => {
    const onChange = vi.fn();
    render(<Pagination pageNo={5} pageSize={10} total={100} onChange={onChange} />);

    expect(screen.getByRole("navigation", { name: "分页" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "5" })).toHaveAttribute("aria-current", "page");
    expect(screen.getAllByText("…")).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "上一页" }));
    fireEvent.click(screen.getByRole("button", { name: "10" }));
    expect(onChange).toHaveBeenNthCalledWith(1, 4);
    expect(onChange).toHaveBeenNthCalledWith(2, 10);
  });
});
