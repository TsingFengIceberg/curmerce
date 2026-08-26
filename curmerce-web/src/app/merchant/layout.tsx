import { MerchantAccessBoundary } from "@/components/merchant-access-boundary";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Suspense } from "react";

export default function MerchantLayout({ children }: { children: React.ReactNode }) {
  return <MerchantAccessBoundary><Suspense fallback={<div className="workspace-access-skeleton"><span /><span /><p>正在加载商家工作区…</p></div>}><WorkspaceShell kind="merchant">{children}</WorkspaceShell></Suspense></MerchantAccessBoundary>;
}
