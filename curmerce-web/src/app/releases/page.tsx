"use client";

import { MapPin, PackageCheck, ShoppingBag, Timer } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { EventCountdown } from "@/components/event-countdown";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { releaseApi } from "@/lib/api/release";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, toDateTimeMillis } from "@/lib/format";
import { memberApi } from "@/lib/api/member";
import type { MemberAddress, ReleaseCampaign, ReleaseItem } from "@/lib/types/api";

const PAGE_SIZE = 12;
const labels: Record<number, string> = { 10: "待开始", 20: "进行中", 30: "已结束", 40: "已取消" };

function isOpen(campaign: ReleaseCampaign, now: number) {
  return (campaign.status === 10 || campaign.status === 20)
    && toDateTimeMillis(campaign.startTime) <= now
    && now < toDateTimeMillis(campaign.endTime);
}

type PurchaseDraft = { campaign: ReleaseCampaign; item: ReleaseItem; quantity: number; addressId: number };

export default function ReleasesPage() {
  const [campaigns, setCampaigns] = useState<ReleaseCampaign[]>([]);
  const [addresses, setAddresses] = useState<MemberAddress[]>([]);
  const [selectedItems, setSelectedItems] = useState<Record<number, number>>({});
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const [purchaseDraft, setPurchaseDraft] = useState<PurchaseDraft | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => { void load(); }, [pageNo]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await releaseApi.page({ pageNo, pageSize: PAGE_SIZE });
      setCampaigns(page.list ?? []);
      setTotal(page.total ?? 0);
      setSelectedItems((current) => {
        const next = { ...current };
        for (const campaign of page.list ?? []) {
          if (!campaign.items.some((item) => item.id === next[campaign.id])) next[campaign.id] = campaign.items.find((item) => item.stock > 0)?.id ?? campaign.items[0]?.id;
        }
        return next;
      });
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "限时发售加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function beginPurchase(campaign: ReleaseCampaign, item: ReleaseItem) {
    if (!getAccessToken()) {
      setError("请先登录后购买限时发售商品");
      return;
    }
    setError(null);
    try {
      const list = addresses.length ? addresses : await memberApi.listAddresses();
      setAddresses(list);
      const address = list.find((entry) => entry.defaultStatus) ?? list[0];
      if (!address) {
        setError("请先添加收货地址");
        return;
      }
      setPurchaseDraft({ campaign, item, quantity: 1, addressId: address.id });
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "收货地址加载失败");
    }
  }

  async function purchase() {
    if (!purchaseDraft) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await releaseApi.purchase({ itemId: purchaseDraft.item.id, quantity: purchaseDraft.quantity, addressId: purchaseDraft.addressId, idempotencyKey: `release-${purchaseDraft.campaign.id}-${purchaseDraft.item.id}-${Date.now()}` });
      setPurchaseDraft(null);
      setMessage(`订单 ${result.orderNo ?? `#${result.orderId}`} 已创建，请前往我的订单完成支付`);
      await load();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "购买失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="content-section commerce-event-page commerce-event-page--productized">
      <div className="section-heading"><div><p className="eyebrow">COMMERCE · LIMITED RELEASE</p><h1>限时发售</h1><p>选择具体商品规格，在活动时间内按限购数量购买。</p></div><Link className="button button--secondary" href="/auctions">去看拍卖</Link></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <div className="event-skeleton"><span /><span /><span /></div> : null}
      {!loading && !campaigns.length ? <EmptyState icon={<Timer aria-hidden="true" size={23} />} title="当前没有限时发售" description="新活动发布后会出现在这里。" action={{ href: "/catalog", label: "先去逛商城" }} /> : null}
      {!loading && campaigns.length ? <div className="event-grid event-grid--productized">{campaigns.map((campaign) => {
        const item = campaign.items.find((entry) => entry.id === selectedItems[campaign.id]) ?? campaign.items[0];
        const open = isOpen(campaign, now);
        const soldOut = !item || item.stock <= 0;
        return <article className="event-card event-card--release" key={campaign.id}>
          <div className="event-card__visual"><MediaImage alt={item?.productName ?? campaign.name} fallback={<span><ShoppingBag aria-hidden="true" size={30} /></span>} src={assetUrl(item?.productImageUrl)} /><span className="tag event-card__status">{labels[campaign.status] ?? `状态 ${campaign.status}`}</span></div>
          <div className="event-card__content"><EventCountdown startTime={campaign.startTime} endTime={campaign.endTime} /><h2>{campaign.name}</h2><p className="event-card__schedule">{formatDateTime(campaign.startTime)} 至 {formatDateTime(campaign.endTime)}</p>
            {campaign.items.length > 1 ? <div className="event-sku-options" role="group" aria-label={`${campaign.name}规格`}>{campaign.items.map((option) => <button aria-pressed={item?.id === option.id} className={item?.id === option.id ? "event-sku-option event-sku-option--active" : "event-sku-option"} disabled={option.stock <= 0} key={option.id} type="button" onClick={() => setSelectedItems((current) => ({ ...current, [campaign.id]: option.id }))}><strong>{option.productName}</strong><span>{option.skuLabel || "默认规格"}</span><small>{option.stock > 0 ? `剩余 ${option.stock}` : "已售罄"}</small></button>)}</div> : item ? <div className="event-selected-sku"><strong>{item.productName}</strong><span>{item.skuLabel || "默认规格"}</span></div> : null}
            {item ? <div className="event-price-block"><div><strong>{formatMoney(item.campaignPrice)}</strong>{item.originalPrice && item.originalPrice > item.campaignPrice ? <del>{formatMoney(item.originalPrice)}</del> : null}</div><span>购买记录：已售 {item.soldCount} 件 · 活动库存剩余 {item.stock} 件</span></div> : null}
            <div className="event-card__limit"><PackageCheck aria-hidden="true" size={15} /><span>每人每个活动 SKU 最多购买 {campaign.perUserLimit} 件</span></div>
            <button className="button button--primary button--full" disabled={!item || soldOut || !open} type="button" onClick={() => item && void beginPurchase(campaign, item)}>{soldOut ? "该规格已售罄" : now < toDateTimeMillis(campaign.startTime) ? "尚未开始" : now >= toDateTimeMillis(campaign.endTime) ? "活动已结束" : "选择地址并购买"}</button>
          </div>
        </article>;
      })}</div> : null}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      <Drawer open={Boolean(purchaseDraft)} title="确认限时发售订单" description="提交后会创建待支付订单并占用活动库存。" busy={busy} onClose={() => setPurchaseDraft(null)}>{purchaseDraft ? <div className="drawer-form"><div className="purchase-summary"><strong>{purchaseDraft.item.productName}</strong><span>{purchaseDraft.item.skuLabel || "默认规格"}</span><b>{formatMoney(purchaseDraft.item.campaignPrice)}</b></div><label className="field"><span>购买数量</span><input max={Math.min(purchaseDraft.campaign.perUserLimit, purchaseDraft.item.stock)} min="1" type="number" value={purchaseDraft.quantity} onChange={(event) => setPurchaseDraft({ ...purchaseDraft, quantity: Number(event.target.value) })} /></label><label className="field"><span>收货地址</span><select value={purchaseDraft.addressId} onChange={(event) => setPurchaseDraft({ ...purchaseDraft, addressId: Number(event.target.value) })}>{addresses.map((address) => <option key={address.id} value={address.id}>{address.defaultStatus ? "[默认] " : ""}{address.name} · {address.areaName ?? ""}{address.detailAddress}</option>)}</select></label><Link className="text-button button--icon-label" href="/addresses"><MapPin aria-hidden="true" size={14} />管理收货地址</Link><div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setPurchaseDraft(null)}>取消</button><button className="button button--primary" disabled={busy} type="button" onClick={() => void purchase()}>{busy ? "提交中…" : `确认支付 ${formatMoney(purchaseDraft.item.campaignPrice * purchaseDraft.quantity)}`}</button></div></div> : null}</Drawer>
    </section>
  );
}
