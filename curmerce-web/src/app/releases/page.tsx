"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { releaseApi } from "@/lib/api/release";
import { CurmerceApiError } from "@/lib/api/client";
import { getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney } from "@/lib/format";
import { memberApi } from "@/lib/api/member";
import type { ReleaseCampaign } from "@/lib/types/api";

const labels: Record<number, string> = { 10: "待开始", 20: "进行中", 30: "已结束", 40: "已取消" };
function isOpen(campaign: ReleaseCampaign) {
  const now = Date.now();
  return (campaign.status === 10 || campaign.status === 20) && new Date(String(campaign.startTime)).getTime() <= now && now < new Date(String(campaign.endTime)).getTime();
}

export default function ReleasesPage() {
  const [items, setItems] = useState<ReleaseCampaign[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  async function load() {
    setLoading(true); setError(null);
    try { setItems((await releaseApi.page({ pageNo: 1, pageSize: 30 })).list ?? []); }
    catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "限时发售加载失败"); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  async function purchase(campaign: ReleaseCampaign) {
    if (!getAccessToken()) { setError("请先登录后购买限时发售商品"); return; }
    const item = campaign.items.find((entry) => entry.stock > 0);
    if (!item) return;
    setBusyId(campaign.id); setError(null); setMessage(null);
    try { const address = await memberApi.getDefaultAddress(); if (!address) { setError("请先维护默认收货地址"); return; } const result = await releaseApi.purchase({ itemId: item.id, quantity: 1, addressId: address.id, idempotencyKey: `release-${campaign.id}-${Date.now()}` }); setMessage(`订单已创建：${result.orderNo ?? `#${result.orderId}`}，请前往订单完成支付`); await load(); }
    catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "购买失败"); }
    finally { setBusyId(null); }
  }

  return <section className="content-section commerce-event-page">
    <div className="section-heading"><div><p className="eyebrow">COMMERCE · LIMITED RELEASE</p><h1>限时发售</h1><p>在指定时间内购买活动 SKU，基础版本使用数据库事务保证库存和重复购买规则。</p></div><Link className="button button--secondary" href="/auctions">去看拍卖 →</Link></div>
    {message ? <Notice tone="success">{message}</Notice> : null}{error ? <Notice>{error}</Notice> : null}
    {loading ? <p className="empty-state">活动加载中…</p> : null}
    {!loading && items.length === 0 ? <p className="empty-state">当前没有公开的限时发售活动。</p> : null}
    <div className="event-grid">{items.map((campaign) => { const available = campaign.items.some((item) => item.stock > 0); const item = campaign.items[0]; const open = isOpen(campaign); return <article className="event-card" key={campaign.id}><div className="event-card__top"><span className="tag">{labels[campaign.status] ?? `状态 ${campaign.status}`}</span><span className="event-card__date">{formatDateTime(campaign.startTime)} - {formatDateTime(campaign.endTime)}</span></div><h2>{campaign.name}</h2><p>每人限购 {campaign.perUserLimit} 件 · {campaign.items.length} 个活动 SKU</p>{item ? <div className="event-card__price"><strong>{formatMoney(item.campaignPrice)}</strong><span>剩余 {item.stock} 件 · 已售 {item.soldCount}</span></div> : null}<button className="button button--primary button--full" disabled={!available || busyId === campaign.id || !open} type="button" onClick={() => void purchase(campaign)}>{busyId === campaign.id ? "提交中…" : !open && campaign.status === 10 ? "尚未开始" : available && open ? "立即购买 1 件" : "已售罄"}</button></article>; })}</div>
  </section>;
}
