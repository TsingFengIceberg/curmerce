"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ensureMerchantOwner } from "@/lib/auth/guards";

export function MerchantAccessBoundary({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    let active = true;
    void ensureMerchantOwner(router).then((nextAllowed) => { if (active && nextAllowed) setAllowed(true); });
    return () => { active = false; };
  }, [router]);

  if (!allowed) return <div aria-live="polite" className="workspace-access-skeleton" role="status"><span /><span /><p>正在验证商家身份…</p></div>;
  return children;
}
