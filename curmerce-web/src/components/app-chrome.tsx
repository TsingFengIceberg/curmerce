"use client";

import { usePathname } from "next/navigation";
import { SiteHeader } from "@/components/site-header";
import { FeedbackCenter } from "@/components/feedback-center";
import { NetworkStatus } from "@/components/network-status";

export function AppChrome({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const workspace = pathname.startsWith("/admin")
    || (pathname.startsWith("/merchant") && pathname !== "/merchant/login");

  return (
    <>
      <SiteHeader />
      <NetworkStatus />
      <main className={workspace ? "workspace-page-shell" : "page-shell"}>{children}</main>
      <FeedbackCenter />
    </>
  );
}
