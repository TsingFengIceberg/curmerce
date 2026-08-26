"use client";

import { AlignJustify, Rows3 } from "lucide-react";
import { useEffect, useState } from "react";

export type TableDensity = "comfortable" | "compact";

export function useTableDensity(storageKey: string) {
  const [density, setDensity] = useState<TableDensity>("comfortable");
  useEffect(() => {
    const saved = window.localStorage.getItem(`curmerce:table-density:${storageKey}`);
    if (saved === "compact" || saved === "comfortable") setDensity(saved);
  }, [storageKey]);
  function update(next: TableDensity) {
    setDensity(next);
    window.localStorage.setItem(`curmerce:table-density:${storageKey}`, next);
  }
  return { density, setDensity: update };
}

export function TableDensityControl({ density, onChange }: { density: TableDensity; onChange: (density: TableDensity) => void }) {
  return <div className="table-density-control" role="group" aria-label="表格密度">
    <button aria-label="舒适表格密度" aria-pressed={density === "comfortable"} title="舒适密度" type="button" onClick={() => onChange("comfortable")}><Rows3 aria-hidden="true" size={15} /></button>
    <button aria-label="紧凑表格密度" aria-pressed={density === "compact"} title="紧凑密度" type="button" onClick={() => onChange("compact")}><AlignJustify aria-hidden="true" size={15} /></button>
  </div>;
}
