import { WorkspaceShell } from "@/components/workspace-shell";

export default function PersonalLayout({ children }: { children: React.ReactNode }) {
  return <MemberAccessBoundary><WorkspaceShell kind="personal">{children}</WorkspaceShell></MemberAccessBoundary>;
}
import { MemberAccessBoundary } from "@/components/member-access-boundary";
