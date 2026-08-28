"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ensureMerchantOwner } from "@/lib/auth/guards";

export function MerchantAccessBoundary({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [allowed, setAllowed] = useState(false);
  const isLoginPage = pathname === "/merchant/login";

  useEffect(() => {
    if (isLoginPage) {
      setAllowed(true);
      return;
    }

    setAllowed(false);
    let active = true;
    void ensureMerchantOwner(router).then((nextAllowed) => { if (active && nextAllowed) setAllowed(true); });
    return () => { active = false; };
  }, [isLoginPage, router]);

  if (isLoginPage) return children;
  if (!allowed) return <div aria-live="polite" className="workspace-access-skeleton" role="status"><span /><span /><p>正在验证商家身份…</p></div>;
  return children;
}
