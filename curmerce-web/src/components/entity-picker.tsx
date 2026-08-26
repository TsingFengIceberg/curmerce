"use client";

import { Check, Search, X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";

export type EntityOption = { id: number; label: string; description?: string };

export function EntityPicker({ label, value, placeholder, onChange, search, resolve }: {
  label: string;
  value?: number;
  placeholder: string;
  onChange: (value?: EntityOption) => void;
  search: (keyword: string) => Promise<EntityOption[]>;
  resolve: (id: number) => Promise<EntityOption | null>;
}) {
  const listId = useId();
  const requestVersion = useRef(0);
  const [selected, setSelected] = useState<EntityOption | null>(null);
  const [keyword, setKeyword] = useState("");
  const [options, setOptions] = useState<EntityOption[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!value) { setSelected(null); return; }
    if (selected?.id === value) return;
    let active = true;
    void resolve(value).then((option) => { if (active) setSelected(option); }).catch(() => { if (active) setSelected({ id: value, label: `${label} ${value}` }); });
    return () => { active = false; };
  }, [label, resolve, selected?.id, value]);

  useEffect(() => {
    if (!open) return;
    const version = ++requestVersion.current;
    setLoading(true);
    setError(null);
    const timer = window.setTimeout(() => {
      void search(keyword.trim()).then((next) => {
        if (version === requestVersion.current) setOptions(next);
      }).catch((cause) => {
        if (version === requestVersion.current) setError(cause instanceof Error ? cause.message : "搜索失败");
      }).finally(() => {
        if (version === requestVersion.current) setLoading(false);
      });
    }, 250);
    return () => window.clearTimeout(timer);
  }, [keyword, open, search]);

  function choose(option: EntityOption) {
    setSelected(option);
    setKeyword("");
    setOpen(false);
    onChange(option);
  }

  return <div className="entity-picker">
    <span>{label}</span>
    {selected ? <div className="entity-picker__selected"><span><strong>{selected.label}</strong>{selected.description ? <small>{selected.description}</small> : null}</span><button aria-label={`清除${label}`} type="button" onClick={() => { setSelected(null); onChange(undefined); }}><X aria-hidden="true" size={15} /></button></div> : <div className="entity-picker__input"><Search aria-hidden="true" size={15} /><input aria-controls={listId} aria-expanded={open} aria-label={`搜索${label}`} autoComplete="off" placeholder={placeholder} role="combobox" value={keyword} onBlur={() => window.setTimeout(() => setOpen(false), 150)} onChange={(event) => { setKeyword(event.target.value); setOpen(true); }} onFocus={() => setOpen(true)} onKeyDown={(event) => { if (event.key === "Escape") setOpen(false); }} /></div>}
    {open && !selected ? <div className="entity-picker__popover" id={listId} role="listbox">
      {loading ? <span>搜索中…</span> : error ? <span>{error}</span> : options.length ? options.map((option) => <button aria-selected="false" key={option.id} role="option" type="button" onMouseDown={(event) => event.preventDefault()} onClick={() => choose(option)}><span><strong>{option.label}</strong>{option.description ? <small>{option.description}</small> : null}</span><Check aria-hidden="true" size={15} /></button>) : <span>{keyword ? "没有匹配结果" : "输入名称、手机号或邮箱搜索"}</span>}
    </div> : null}
  </div>;
}
