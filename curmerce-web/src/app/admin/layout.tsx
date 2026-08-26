import { AdminAccessBoundary } from "@/components/admin-access-boundary";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Suspense } from "react";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AdminAccessBoundary><Suspense fallback={<div className="workspace-access-skeleton"><span /><span /><p>正在加载平台工作区…</p></div>}><WorkspaceShell kind="admin">{children}</WorkspaceShell></Suspense></AdminAccessBoundary>;
}
