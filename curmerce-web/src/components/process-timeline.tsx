import { AlertCircle, CheckCircle2, Circle, Clock3 } from "lucide-react";
import type { ReactNode } from "react";

export type ProcessStep = {
  id: string;
  label: string;
  time?: string;
  description?: ReactNode;
  state: "done" | "current" | "pending" | "error";
};

export function ProcessTimeline({ steps, label = "流程进度", compact = false }: { steps: ProcessStep[]; label?: string; compact?: boolean }) {
  return (
    <ol aria-label={label} className={`process-timeline${compact ? " process-timeline--compact" : ""}`}>
      {steps.map((step) => {
        const Icon = step.state === "done" ? CheckCircle2 : step.state === "error" ? AlertCircle : step.state === "current" ? Clock3 : Circle;
        return <li className={`process-timeline__step process-timeline__step--${step.state}`} key={step.id}>
          <span className="process-timeline__rail" aria-hidden="true" />
          <Icon aria-hidden="true" className="process-timeline__icon" size={18} />
          <div><strong>{step.label}</strong>{step.description ? <span>{step.description}</span> : null}</div>
          <time>{step.time || (step.state === "pending" ? "等待中" : "")}</time>
        </li>;
      })}
    </ol>
  );
}
