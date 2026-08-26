"use client";

import { AlertCircle } from "lucide-react";
import { useEffect, useRef } from "react";

export type FormIssue = { field: string; message: string };

export function FormErrorSummary({ issues }: { issues: FormIssue[] }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { if (issues.length) ref.current?.focus(); }, [issues]);
  if (!issues.length) return null;
  return <div aria-labelledby="form-error-title" className="form-error-summary" ref={ref} role="alert" tabIndex={-1}><div><AlertCircle aria-hidden="true" size={18} /><strong id="form-error-title">请检查以下内容</strong></div><ul>{issues.map((issue) => <li key={`${issue.field}-${issue.message}`}><a href={`#${issue.field}`}>{issue.message}</a></li>)}</ul></div>;
}
