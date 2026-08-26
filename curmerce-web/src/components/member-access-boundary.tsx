"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { currentLocation, loginPath } from "@/lib/auth/guards";
import { getAccessToken } from "@/lib/auth/storage";

export function MemberAccessBoundary({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    if (getAccessToken()) setAllowed(true);
    else router.replace(loginPath("/login", currentLocation()));
  }, [router]);

  if (!allowed) return <div aria-live="polite" className="workspace-access-skeleton" role="status"><span /><span /><p>正在验证用户身份…</p></div>;
  return children;
}
