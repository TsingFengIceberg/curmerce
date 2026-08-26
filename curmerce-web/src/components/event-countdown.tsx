"use client";

import { Clock3 } from "lucide-react";
import { useEffect, useState } from "react";
import { toDateTimeMillis } from "@/lib/format";

function durationLabel(milliseconds: number) {
  const seconds = Math.max(0, Math.floor(milliseconds / 1000));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;
  return `${days ? `${days}天 ` : ""}${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(remainingSeconds).padStart(2, "0")}`;
}

export function EventCountdown({ startTime, endTime }: { startTime?: string | number | null; endTime?: string | number | null }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const start = toDateTimeMillis(startTime);
  const end = toDateTimeMillis(endTime);
  const label = now < start ? `距离开始 ${durationLabel(start - now)}` : now < end ? `距离结束 ${durationLabel(end - now)}` : "活动已结束";
  return <span className="event-countdown"><Clock3 aria-hidden="true" size={14} />{label}</span>;
}
