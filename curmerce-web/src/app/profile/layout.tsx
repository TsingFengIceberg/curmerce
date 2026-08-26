import { WorkspaceShell } from "@/components/workspace-shell";

export default function ProfileLayout({ children }: { children: React.ReactNode }) {
  return <WorkspaceShell kind="buyer">{children}</WorkspaceShell>;
}
