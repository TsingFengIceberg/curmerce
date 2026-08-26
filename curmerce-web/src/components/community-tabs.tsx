"use client";

import { Bookmark, Compass, PenSquare, UserRoundCheck, FileText } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

const tabs = [
  { href: "/community", label: "发现", icon: Compass, exact: true },
  { href: "/community/following", label: "关注", icon: UserRoundCheck },
  { href: "/community/favorites", label: "收藏", icon: Bookmark },
  { href: "/community/mine", label: "我的帖子", icon: FileText },
];

export function CommunityTabs() {
  const pathname = usePathname();
  return (
    <div className="community-subnav">
      <nav aria-label="社区导航">
        {tabs.map(({ href, label, icon: Icon, exact }) => {
          const active = exact ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);
          return <Link aria-current={active ? "page" : undefined} className={active ? "community-subnav__item community-subnav__item--active" : "community-subnav__item"} href={href} key={href}><Icon aria-hidden="true" size={16} />{label}</Link>;
        })}
      </nav>
      <Link className="button button--primary button--small button--icon-label" href="/community/create"><PenSquare aria-hidden="true" size={16} />发布</Link>
    </div>
  );
}
