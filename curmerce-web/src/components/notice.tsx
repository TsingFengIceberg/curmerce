"use client";

import { CheckCircle2, X } from "lucide-react";
import { isValidElement, useEffect, useState } from "react";

function contentKey(node: React.ReactNode): string {
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(contentKey).join("|");
  if (isValidElement<{ children?: React.ReactNode }>(node)) return contentKey(node.props.children);
  return "";
}

export function Notice({ tone = "error", children }: { tone?: "error" | "success" | "info"; children: React.ReactNode }) {
  const [visible, setVisible] = useState(true);
  const key = contentKey(children);

  useEffect(() => {
    setVisible(true);
    if (tone !== "success") return;
    const timer = window.setTimeout(() => setVisible(false), 4_500);
    return () => window.clearTimeout(timer);
  }, [key, tone]);

  if (!visible) return null;
  if (tone === "success") {
    return <div aria-live="polite" className="ui-toast ui-toast--success" role="status"><CheckCircle2 aria-hidden="true" size={19} /><span>{children}</span><button aria-label="关闭提示" type="button" onClick={() => setVisible(false)}><X aria-hidden="true" size={17} /></button></div>;
  }
  return <div aria-live={tone === "error" ? "assertive" : "polite"} className={`notice notice--${tone}`} role={tone === "error" ? "alert" : "status"}>{children}</div>;
}
