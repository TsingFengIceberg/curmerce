import Link from "next/link";
import type { LucideIcon } from "lucide-react";
import { ArrowRight } from "lucide-react";

export interface DashboardMetric {
  label: string;
  value: number | string;
  hint: string;
  href: string;
  icon: LucideIcon;
}

export function DashboardHeader({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return <header className="workspace-page-heading"><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></header>;
}

export function DashboardMetrics({ items, loading = false }: { items: DashboardMetric[]; loading?: boolean }) {
  return (
    <div className="metric-grid" aria-busy={loading}>
      {items.map(({ label, value, hint, href, icon: Icon }) => (
        <Link className="metric-tile" href={href} key={label}>
          <div className="metric-tile__top"><span className="metric-tile__icon"><Icon aria-hidden="true" size={19} /></span><ArrowRight aria-hidden="true" size={17} /></div>
          <strong>{loading ? "--" : value}</strong><span>{label}</span><small>{hint}</small>
        </Link>
      ))}
    </div>
  );
}

export function QuickActions({ title, items }: { title: string; items: Array<{ href: string; label: string; description: string }> }) {
  return (
    <section className="workspace-section">
      <div className="workspace-section__heading"><h2>{title}</h2></div>
      <div className="quick-action-list">
        {items.map((item) => <Link href={item.href} key={item.href}><div><strong>{item.label}</strong><span>{item.description}</span></div><ArrowRight aria-hidden="true" size={18} /></Link>)}
      </div>
    </section>
  );
}
