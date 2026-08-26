import { WorkspaceShell } from "@/components/workspace-shell";

export default function OrdersLayout({ children }: { children: React.ReactNode }) {
  return <WorkspaceShell kind="buyer">{children}</WorkspaceShell>;
}
