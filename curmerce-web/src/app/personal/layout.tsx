import { WorkspaceShell } from "@/components/workspace-shell";

export default function PersonalLayout({ children }: { children: React.ReactNode }) {
  return <WorkspaceShell kind="personal">{children}</WorkspaceShell>;
}
