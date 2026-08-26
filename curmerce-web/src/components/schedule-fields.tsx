"use client";

import { CalendarClock, Clock3, TriangleAlert } from "lucide-react";
import { formatBeijingDateTime, toDateTimeMillis } from "@/lib/format";

type Window = { id: number; name: string; startTime: string | number | null; endTime: string | number | null };

function localInput(millis: number) {
  const date = new Date(millis);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function durationLabel(millis: number) {
  if (!Number.isFinite(millis) || millis <= 0) return "结束时间需晚于开始时间";
  const hours = millis / 3_600_000;
  if (hours < 24) return `持续 ${hours.toFixed(hours % 1 ? 1 : 0)} 小时`;
  const days = hours / 24;
  return `持续 ${days.toFixed(days % 1 ? 1 : 0)} 天`;
}

export function ScheduleFields({ startTime, endTime, onChange, idPrefix, windows = [], excludeId }: {
  startTime: string;
  endTime: string;
  onChange: (next: { startTime: string; endTime: string }) => void;
  idPrefix: string;
  windows?: Window[];
  excludeId?: number;
}) {
  const start = toDateTimeMillis(startTime);
  const end = toDateTimeMillis(endTime);
  const conflicts = Number.isFinite(start) && Number.isFinite(end) ? windows.filter((window) => window.id !== excludeId && start < toDateTimeMillis(window.endTime) && end > toDateTimeMillis(window.startTime)) : [];

  function quickStart(offset: number) {
    const nextStart = Date.now() + offset;
    const duration = Number.isFinite(end - start) && end > start ? end - start : 86_400_000;
    onChange({ startTime: localInput(nextStart), endTime: localInput(nextStart + duration) });
  }

  function setDuration(hours: number) {
    const nextStart = Number.isFinite(start) ? start : Date.now() + 3_600_000;
    onChange({ startTime: localInput(nextStart), endTime: localInput(nextStart + hours * 3_600_000) });
  }

  return <section className="schedule-fields" aria-label="活动排期">
    <div className="schedule-fields__shortcuts"><span><CalendarClock aria-hidden="true" size={15} />快速排期</span><button type="button" onClick={() => quickStart(3_600_000)}>1 小时后</button><button type="button" onClick={() => quickStart(86_400_000)}>明天此时</button><button type="button" onClick={() => setDuration(2)}>2 小时</button><button type="button" onClick={() => setDuration(24)}>1 天</button><button type="button" onClick={() => setDuration(72)}>3 天</button></div>
    <div className="admin-form-grid">
      <label className="field"><span>开始时间</span><input id={`${idPrefix}-start-time`} required type="datetime-local" value={startTime} onChange={(event) => onChange({ startTime: event.target.value, endTime })} /><small className="field-help">{formatBeijingDateTime(startTime)}</small></label>
      <label className="field"><span>结束时间</span><input id={`${idPrefix}-end-time`} required type="datetime-local" value={endTime} onChange={(event) => onChange({ startTime, endTime: event.target.value })} /><small className="field-help">{formatBeijingDateTime(endTime)}</small></label>
    </div>
    <div className="schedule-fields__summary"><span><Clock3 aria-hidden="true" size={14} />{durationLabel(end - start)} · 页面按本地时区输入，提交时按北京时间解释</span>{conflicts.length ? <strong><TriangleAlert aria-hidden="true" size={14} />与 {conflicts.slice(0, 2).map((item) => item.name).join("、")} 时间重叠，请确认库存与运营安排</strong> : <span>未发现同类活动排期冲突</span>}</div>
  </section>;
}
