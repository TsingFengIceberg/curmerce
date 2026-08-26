"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";

export function Pagination({ pageNo, pageSize, total, onChange }: { pageNo: number; pageSize: number; total: number; onChange: (page: number) => void }) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  if (total <= pageSize) return null;
  const pages = Array.from(new Set([1, pageNo - 1, pageNo, pageNo + 1, pageCount].filter((page) => page >= 1 && page <= pageCount)));

  return (
    <nav className="ui-pagination" aria-label="分页">
      <button aria-label="上一页" disabled={pageNo <= 1} type="button" onClick={() => onChange(pageNo - 1)}><ChevronLeft aria-hidden="true" size={17} /></button>
      {pages.map((page, index) => <span className="ui-pagination__slot" key={page}>{index > 0 && page - pages[index - 1] > 1 ? <span aria-hidden="true">…</span> : null}<button aria-current={page === pageNo ? "page" : undefined} className={page === pageNo ? "ui-pagination__page ui-pagination__page--active" : "ui-pagination__page"} type="button" onClick={() => onChange(page)}>{page}</button></span>)}
      <button aria-label="下一页" disabled={pageNo >= pageCount} type="button" onClick={() => onChange(pageNo + 1)}><ChevronRight aria-hidden="true" size={17} /></button>
      <small>共 {total} 条</small>
    </nav>
  );
}
