"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ensurePlatformAdmin } from "@/lib/auth/guards";

export function AdminAccessBoundary({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    let active = true;
    void ensurePlatformAdmin(router).then((nextAllowed) => {
      if (active && nextAllowed) setAllowed(true);
    });
    return () => {
      active = false;
    };
  }, [router]);

  if (!allowed) {
    return <div aria-live="polite" className="workspace-access-skeleton" role="status"><span /><span /><p>正在验证平台管理员身份…</p></div>;
  }
  return children;
}
