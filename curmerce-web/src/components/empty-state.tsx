import Link from "next/link";
import { Inbox } from "lucide-react";
import { ReactNode } from "react";

type EmptyStateProps = {
  title: string;
  description?: string;
  icon?: ReactNode;
  action?: { href: string; label: string };
  actionLabel?: string;
  onAction?: () => void;
};

export function EmptyState({ title, description, icon, action, actionLabel, onAction }: EmptyStateProps) {
  return (
    <div className="ui-empty-state">
      <span>{icon ?? <Inbox aria-hidden="true" size={23} />}</span>
      <strong>{title}</strong>
      {description ? <p>{description}</p> : null}
      {action ? <Link className="button button--secondary button--small" href={action.href}>{action.label}</Link> : null}
      {actionLabel && onAction ? <button className="button button--secondary button--small" type="button" onClick={onAction}>{actionLabel}</button> : null}
    </div>
  );
}
