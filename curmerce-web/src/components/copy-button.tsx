"use client";

import { Check, Copy } from "lucide-react";
import { useState } from "react";

export function CopyButton({ value, label = "复制" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1600);
  }
  return <button className="copy-button" title={`${label}${value}`} type="button" onClick={() => void copy()}>{copied ? <Check aria-hidden="true" size={15} /> : <Copy aria-hidden="true" size={15} />}<span>{copied ? "已复制" : label}</span></button>;
}
