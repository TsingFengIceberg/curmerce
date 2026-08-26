"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import {
  ChevronRight,
  CircleUserRound,
  FileHeart,
  Heart,
  MapPin,
  MessageSquareText,
  PackageCheck,
  ReceiptText,
  RotateCcw,
  ShoppingBag,
  UsersRound,
} from "lucide-react";
import { getAccessToken } from "@/lib/auth/storage";

const groups = [
  {
    title: "交易与售后",
    items: [
      { href: "/orders", label: "买入订单", description: "支付、物流和确认收货", icon: ReceiptText },
      { href: "/refunds", label: "退款中心", description: "查看退款申请与处理进度", icon: RotateCcw },
      { href: "/account/product-favorites", label: "商品收藏", description: "回看感兴趣的在售商品", icon: Heart },
      { href: "/cart", label: "购物车", description: "继续处理已选商品", icon: ShoppingBag },
    ],
  },
  {
    title: "社区与兴趣",
    items: [
      { href: "/community/following", label: "关注动态", description: "查看关注作者的新内容", icon: UsersRound },
      { href: "/community/favorites", label: "帖子收藏", description: "回看收藏的社区内容", icon: Heart },
      { href: "/community/mine", label: "我的帖子", description: "管理草稿和已发布内容", icon: MessageSquareText },
    ],
  },
  {
    title: "个人卖家",
    items: [
      { href: "/personal", label: "卖家中心", description: "查看闲置商品与待办", icon: FileHeart },
      { href: "/personal/listings", label: "我的闲置", description: "发布和管理一件一库存商品", icon: PackageCheck },
      { href: "/personal/orders", label: "卖出订单", description: "处理发货并跟踪交易", icon: ReceiptText },
    ],
  },
];

export default function AccountPage() {
  const router = useRouter();
  useEffect(() => { if (!getAccessToken()) router.replace("/login"); }, [router]);

  return (
    <section className="content-section account-page">
      <header className="account-heading"><div className="account-heading__avatar"><CircleUserRound aria-hidden="true" /></div><div><p className="eyebrow">MY CURMERCE</p><h1>我的</h1><p>订单、内容、地址和卖家事务都从这里进入。</p></div></header>
      <div className="account-layout">
        <div className="account-groups">
          {groups.map((group) => <section className="account-group" key={group.title}><h2>{group.title}</h2><div className="account-link-list">{group.items.map(({ href, label, description, icon: Icon }) => <Link href={href} key={href}><span className="account-link__icon"><Icon aria-hidden="true" size={20} /></span><span><strong>{label}</strong><small>{description}</small></span><ChevronRight aria-hidden="true" size={18} /></Link>)}</div></section>)}
        </div>
        <aside className="account-settings"><h2>账户设置</h2><Link href="/profile"><CircleUserRound aria-hidden="true" size={19} /><span><strong>个人资料</strong><small>昵称、联系方式与头像</small></span><ChevronRight aria-hidden="true" size={18} /></Link><Link href="/addresses"><MapPin aria-hidden="true" size={19} /><span><strong>收货地址</strong><small>维护默认地址和常用地址</small></span><ChevronRight aria-hidden="true" size={18} /></Link></aside>
      </div>
    </section>
  );
}
