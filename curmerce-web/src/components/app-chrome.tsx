"use client";

import { usePathname } from "next/navigation";
import { SiteHeader } from "@/components/site-header";

export function AppChrome({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const workspace = pathname.startsWith("/admin")
    || (pathname.startsWith("/merchant") && pathname !== "/merchant/login");

  return (
    <>
      <SiteHeader />
      <main className={workspace ? "workspace-page-shell" : "page-shell"}>{children}</main>
    </>
  );
}
