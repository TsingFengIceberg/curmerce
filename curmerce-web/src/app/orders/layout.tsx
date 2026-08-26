import { WorkspaceShell } from "@/components/workspace-shell";

export default function OrdersLayout({ children }: { children: React.ReactNode }) {
  return <MemberAccessBoundary><WorkspaceShell kind="buyer">{children}</WorkspaceShell></MemberAccessBoundary>;
}
import { MemberAccessBoundary } from "@/components/member-access-boundary";
