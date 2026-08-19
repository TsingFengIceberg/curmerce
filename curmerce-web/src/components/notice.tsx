export function Notice({ tone = "error", children }: { tone?: "error" | "success" | "info"; children: React.ReactNode }) {
  return <div className={`notice notice--${tone}`}>{children}</div>;
}
