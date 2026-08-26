"use client";

import { AlertCircle, CheckCircle2, Info, LoaderCircle, RotateCcw, X } from "lucide-react";
import { useEffect, useState } from "react";

type FeedbackTone = "success" | "error" | "info" | "loading";

export type FeedbackInput = {
  id?: string;
  tone: FeedbackTone;
  title: string;
  description?: string;
  duration?: number;
  actionLabel?: string;
  onAction?: () => void | Promise<void>;
};

type FeedbackEvent = FeedbackInput & { id: string };

const FEEDBACK_EVENT = "curmerce:feedback";
const FEEDBACK_DISMISS_EVENT = "curmerce:feedback-dismiss";

function id() {
  return `feedback-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function notifyFeedback(input: FeedbackInput) {
  const event = { ...input, id: input.id ?? id() };
  if (typeof window !== "undefined") window.dispatchEvent(new CustomEvent<FeedbackEvent>(FEEDBACK_EVENT, { detail: event }));
  return event.id;
}

export function dismissFeedback(feedbackId: string) {
  if (typeof window !== "undefined") window.dispatchEvent(new CustomEvent<string>(FEEDBACK_DISMISS_EVENT, { detail: feedbackId }));
}

export async function withFeedback<T>(operation: () => Promise<T>, labels: {
  loading: string;
  success: string;
  error: string;
  retry?: () => void | Promise<void>;
}) {
  const feedbackId = notifyFeedback({ tone: "loading", title: labels.loading, duration: 0 });
  try {
    const result = await operation();
    notifyFeedback({ id: feedbackId, tone: "success", title: labels.success });
    return result;
  } catch (cause) {
    notifyFeedback({
      id: feedbackId,
      tone: "error",
      title: labels.error,
      description: cause instanceof Error ? cause.message : undefined,
      duration: 0,
      actionLabel: labels.retry ? "重试" : undefined,
      onAction: labels.retry,
    });
    throw cause;
  }
}

export function FeedbackCenter() {
  const [items, setItems] = useState<FeedbackEvent[]>([]);

  useEffect(() => {
    const receive = (event: Event) => {
      const next = (event as CustomEvent<FeedbackEvent>).detail;
      setItems((current) => [...current.filter((item) => item.id !== next.id), next].slice(-4));
    };
    const dismiss = (event: Event) => setItems((current) => current.filter((item) => item.id !== (event as CustomEvent<string>).detail));
    window.addEventListener(FEEDBACK_EVENT, receive);
    window.addEventListener(FEEDBACK_DISMISS_EVENT, dismiss);
    return () => {
      window.removeEventListener(FEEDBACK_EVENT, receive);
      window.removeEventListener(FEEDBACK_DISMISS_EVENT, dismiss);
    };
  }, []);

  useEffect(() => {
    const timers = items.filter((item) => item.tone !== "loading" && item.duration !== 0).map((item) => window.setTimeout(() => {
      setItems((current) => current.filter((entry) => entry.id !== item.id));
    }, item.duration ?? (item.tone === "error" ? 8_000 : 4_500)));
    return () => timers.forEach(window.clearTimeout);
  }, [items]);

  return (
    <section aria-label="操作通知" aria-live="polite" className="feedback-center">
      {items.map((item) => {
        const Icon = item.tone === "success" ? CheckCircle2 : item.tone === "error" ? AlertCircle : item.tone === "loading" ? LoaderCircle : Info;
        return <article className={`feedback-item feedback-item--${item.tone}`} key={item.id} role={item.tone === "error" ? "alert" : "status"}>
          <Icon aria-hidden="true" className={item.tone === "loading" ? "feedback-item__spinner" : ""} size={19} />
          <div><strong>{item.title}</strong>{item.description ? <span>{item.description}</span> : null}</div>
          {item.actionLabel && item.onAction ? <button className="feedback-item__action" type="button" onClick={() => { void item.onAction?.(); dismissFeedback(item.id); }}><RotateCcw aria-hidden="true" size={14} />{item.actionLabel}</button> : null}
          <button aria-label="关闭通知" className="feedback-item__close" type="button" onClick={() => dismissFeedback(item.id)}><X aria-hidden="true" size={16} /></button>
        </article>;
      })}
    </section>
  );
}
