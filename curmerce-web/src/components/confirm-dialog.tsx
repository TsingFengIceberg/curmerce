"use client";

import { AlertTriangle, X } from "lucide-react";
import { useEffect, useId, useRef } from "react";

export function ConfirmDialog({ open, title, description, confirmLabel = "确认", dangerous = false, busy = false, onConfirm, onClose }: { open: boolean; title: string; description: string; confirmLabel?: string; dangerous?: boolean; busy?: boolean; onConfirm: () => void; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog aria-describedby={descriptionId} aria-labelledby={titleId} className="confirm-dialog" ref={ref} onCancel={(event) => { event.preventDefault(); onClose(); }} onClose={onClose}>
      <button aria-label="关闭" className="confirm-dialog__close" disabled={busy} type="button" onClick={onClose}><X aria-hidden="true" size={19} /></button>
      <span className={dangerous ? "confirm-dialog__icon confirm-dialog__icon--danger" : "confirm-dialog__icon"}><AlertTriangle aria-hidden="true" size={22} /></span>
      <h2 id={titleId}>{title}</h2><p id={descriptionId}>{description}</p>
      <div className="confirm-dialog__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={onClose}>返回</button><button className={dangerous ? "button button--danger" : "button button--primary"} disabled={busy} type="button" onClick={onConfirm}>{busy ? "处理中…" : confirmLabel}</button></div>
    </dialog>
  );
}
