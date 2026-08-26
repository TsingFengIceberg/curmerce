"use client";

import { useEffect, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";

export function useUnsavedClose({ dirty, onDiscard, subject = "当前编辑内容" }: { dirty: boolean; onDiscard: () => void; subject?: string }) {
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  function requestClose() {
    if (dirty) setConfirmOpen(true);
    else onDiscard();
  }

  const confirmation = <ConfirmDialog open={confirmOpen} title={`放弃${subject}？`} description="尚未保存的修改将丢失，此操作无法撤销。" confirmLabel="放弃修改" dangerous onClose={() => setConfirmOpen(false)} onConfirm={() => { setConfirmOpen(false); onDiscard(); }} />;
  return { requestClose, confirmation };
}
