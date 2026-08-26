import { WorkspaceShell } from "@/components/workspace-shell";

export default function MerchantLayout({ children }: { children: React.ReactNode }) {
  return <WorkspaceShell kind="merchant">{children}</WorkspaceShell>;
}
