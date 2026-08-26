"use client";

import { X } from "lucide-react";
import { ReactNode, useEffect, useId, useRef } from "react";

type DrawerProps = {
  open: boolean;
  title: string;
  description?: string;
  children: ReactNode;
  busy?: boolean;
  onClose: () => void;
};

export function Drawer({ open, title, description, children, busy = false, onClose }: DrawerProps) {
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
    <dialog
      aria-describedby={description ? descriptionId : undefined}
      aria-labelledby={titleId}
      className="ui-drawer"
      ref={ref}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClose={() => {
        if (open && !busy) onClose();
      }}
    >
      <div className="ui-drawer__header">
        <div>
          <h2 id={titleId}>{title}</h2>
          {description ? <p id={descriptionId}>{description}</p> : null}
        </div>
        <button aria-label="关闭" className="icon-button" disabled={busy} title="关闭" type="button" onClick={onClose}>
          <X aria-hidden="true" size={19} />
        </button>
      </div>
      <div className="ui-drawer__body">{children}</div>
    </dialog>
  );
}
