import type { Metadata } from "next";
import "./globals.css";
import "./product-ui.css";
import { AppChrome } from "@/components/app-chrome";

export const metadata: Metadata = {
  title: "Curmerce",
  description: "Curmerce 社区内容驱动的兴趣消费平台",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <AppChrome>{children}</AppChrome>
      </body>
    </html>
  );
}
