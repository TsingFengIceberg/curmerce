import type { Metadata } from "next";
import "./globals.css";
import { SiteHeader } from "@/components/site-header";

export const metadata: Metadata = {
  title: "Curmerce",
  description: "Curmerce 社区内容驱动的兴趣消费平台",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <SiteHeader />
        <main className="page-shell">{children}</main>
      </body>
    </html>
  );
}
