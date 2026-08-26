"use client";

import { WifiOff } from "lucide-react";
import { useEffect, useState } from "react";
import { notifyFeedback } from "@/components/feedback-center";

export function NetworkStatus() {
  const [offline, setOffline] = useState(false);
  useEffect(() => {
    const update = () => {
      const next = !navigator.onLine;
      setOffline(next);
      if (!next) notifyFeedback({ tone: "success", title: "网络已恢复", description: "可以继续刚才的操作" });
    };
    setOffline(!navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => { window.removeEventListener("online", update); window.removeEventListener("offline", update); };
  }, []);
  return offline ? <div className="network-status" role="alert"><WifiOff aria-hidden="true" size={16} /><span>网络连接已断开，未完成的操作会保留在当前页面</span></div> : null;
}
