import { AdminAccessBoundary } from "@/components/admin-access-boundary";
import { WorkspaceShell } from "@/components/workspace-shell";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AdminAccessBoundary><WorkspaceShell kind="admin">{children}</WorkspaceShell></AdminAccessBoundary>;
}
