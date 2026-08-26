import { MerchantAccessBoundary } from "@/components/merchant-access-boundary";
import { WorkspaceShell } from "@/components/workspace-shell";

export default function MerchantLayout({ children }: { children: React.ReactNode }) {
  return <MerchantAccessBoundary><WorkspaceShell kind="merchant">{children}</WorkspaceShell></MerchantAccessBoundary>;
}
