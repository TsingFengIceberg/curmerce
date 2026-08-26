"use client";

import { CheckCircle2, ChevronDown, Gavel, History, MapPin, PackageSearch, Trophy } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { EventCountdown } from "@/components/event-countdown";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { auctionApi } from "@/lib/api/auction";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { getAccessToken } from "@/lib/auth/storage";
import { memberApi } from "@/lib/api/member";
import { formatDateTime, formatMoney, toDateTimeMillis } from "@/lib/format";
import type { AuctionBid, AuctionSession, MemberAddress, MemberProfile } from "@/lib/types/api";

const PAGE_SIZE = 10;
const labels: Record<number, string> = { 10: "待开始", 20: "进行中", 30: "已结束", 40: "已取消", 50: "结算失败" };

function isOpen(session: AuctionSession, now: number) {
  return (session.status === 10 || session.status === 20)
    && toDateTimeMillis(session.startTime) <= now
    && now < toDateTimeMillis(session.endTime);
}

export default function AuctionsPage() {
  const [sessions, setSessions] = useState<AuctionSession[]>([]);
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [addresses, setAddresses] = useState<MemberAddress[]>([]);
  const [amounts, setAmounts] = useState<Record<number, string>>({});
  const [histories, setHistories] = useState<Record<number, AuctionBid[]>>({});
  const [historyTotals, setHistoryTotals] = useState<Record<number, number>>({});
  const [historyPages, setHistoryPages] = useState<Record<number, number>>({});
  const [historyLoading, setHistoryLoading] = useState<Record<number, boolean>>({});
  const [historyOpen, setHistoryOpen] = useState<Record<number, boolean>>({});
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const [pendingBid, setPendingBid] = useState<{ session: AuctionSession; amount: number } | null>(null);
  const [settlement, setSettlement] = useState<{ session: AuctionSession; addressId: number } | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void load();
    const refresh = window.setInterval(() => void load(false), 10_000);
    return () => window.clearInterval(refresh);
  }, [pageNo]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    if (getAccessToken()) void memberApi.getProfile().then(setProfile).catch(() => undefined);
    return () => window.clearInterval(timer);
  }, []);

  async function load(showLoading = true) {
    if (showLoading) setLoading(true);
    try {
      const page = await auctionApi.page({ pageNo, pageSize: PAGE_SIZE });
      setSessions(page.list ?? []);
      setTotal(page.total ?? 0);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "拍卖加载失败");
    } finally {
      if (showLoading) setLoading(false);
    }
  }

  function minimumBid(session: AuctionSession) {
    return session.currentAmount == null ? session.startingPrice : session.currentAmount + session.minIncrement;
  }

  function setQuickBid(session: AuctionSession, increments: number) {
    const amount = session.currentAmount == null
      ? session.startingPrice + session.minIncrement * (increments - 1)
      : session.currentAmount + session.minIncrement * increments;
    setAmounts((current) => ({ ...current, [session.id]: (amount / 100).toFixed(2) }));
  }

  function beginBid(session: AuctionSession) {
    if (!getAccessToken()) {
      setError("请先登录后出价");
      return;
    }
    const amount = Math.round(Number(amounts[session.id]) * 100);
    const minimum = minimumBid(session);
    if (!Number.isFinite(amount) || amount < minimum) {
      setError(`本次出价至少为 ${formatMoney(minimum)}`);
      return;
    }
    setError(null);
    setPendingBid({ session, amount });
  }

  async function bid() {
    if (!pendingBid) return;
    setBusy(true);
    setError(null);
    try {
      await auctionApi.bid({ sessionId: pendingBid.session.id, amount: pendingBid.amount, idempotencyKey: `web-${pendingBid.session.id}-${Date.now()}` });
      setMessage(`已成功出价 ${formatMoney(pendingBid.amount)}`);
      setAmounts((current) => ({ ...current, [pendingBid.session.id]: "" }));
      const sessionId = pendingBid.session.id;
      setPendingBid(null);
      await Promise.all([load(false), loadHistory(sessionId)]);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "出价失败");
    } finally {
      setBusy(false);
    }
  }

  async function toggleHistory(sessionId: number) {
    const nextOpen = !historyOpen[sessionId];
    setHistoryOpen((current) => ({ ...current, [sessionId]: nextOpen }));
    if (nextOpen && !histories[sessionId]) await loadHistory(sessionId);
  }

  async function loadHistory(sessionId: number, targetPage = 1) {
    setHistoryLoading((current) => ({ ...current, [sessionId]: true }));
    try {
      const page = await auctionApi.bidPage({ sessionId, pageNo: targetPage, pageSize: 10 });
      setHistories((current) => ({
        ...current,
        [sessionId]: targetPage === 1 ? page.list ?? [] : [...(current[sessionId] ?? []), ...(page.list ?? [])],
      }));
      setHistoryTotals((current) => ({ ...current, [sessionId]: page.total ?? 0 }));
      setHistoryPages((current) => ({ ...current, [sessionId]: targetPage }));
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "竞价记录加载失败");
    } finally {
      setHistoryLoading((current) => ({ ...current, [sessionId]: false }));
    }
  }

  async function beginSettlement(session: AuctionSession) {
    setError(null);
    try {
      const list = addresses.length ? addresses : await memberApi.listAddresses();
      setAddresses(list);
      const address = list.find((item) => item.defaultStatus) ?? list[0];
      if (!address) {
        setError("请先添加收货地址");
        return;
      }
      setSettlement({ session, addressId: address.id });
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "收货地址加载失败");
    }
  }

  async function settle() {
    if (!settlement) return;
    setBusy(true);
    setError(null);
    try {
      const orderId = await auctionApi.settle({ sessionId: settlement.session.id, addressId: settlement.addressId });
      setSettlement(null);
      setMessage(`拍卖结算成功，待支付订单 #${orderId} 已创建`);
      await load(false);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "结算失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="content-section commerce-event-page commerce-event-page--productized">
      <div className="section-heading"><div><p className="eyebrow">COMMERCE · AUCTION</p><h1>拍卖</h1><p>查看商品和实时领先价格，按最低加价规则参与竞拍。</p></div><Link className="button button--secondary" href="/releases">去看限时发售</Link></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <div className="event-skeleton"><span /><span /></div> : null}
      {!loading && !sessions.length ? <EmptyState icon={<Gavel aria-hidden="true" size={23} />} title="当前没有公开拍卖" description="新场次发布后会出现在这里。" action={{ href: "/catalog", label: "先去逛商城" }} /> : null}
      {!loading && sessions.length ? <div className="auction-list">{sessions.map((session) => {
        const minimum = minimumBid(session);
        const open = isOpen(session, now);
        const leadingMine = Boolean(profile && session.currentBidderUserId === profile.id);
        const winnerMine = Boolean(profile && session.winnerUserId === profile.id);
        return <article className="auction-card" key={session.id}>
          <div className="auction-card__visual"><MediaImage alt={session.productName ?? session.name} fallback={<span><PackageSearch aria-hidden="true" size={32} /></span>} src={assetUrl(session.productImageUrl)} /><span className="tag auction-card__status">{labels[session.status] ?? `状态 ${session.status}`}</span></div>
          <div className="auction-card__main"><EventCountdown startTime={session.startTime} endTime={session.endTime} /><h2>{session.name}</h2><Link className="auction-card__product" href={`/products/${session.productId}`}><strong>{session.productName || "查看拍卖商品"}</strong><span>{session.skuLabel || "默认规格"}</span></Link><div className="auction-price"><span>当前价</span><strong>{session.currentAmount == null ? formatMoney(session.startingPrice) : formatMoney(session.currentAmount)}</strong>{session.originalPrice ? <small>商品原价 {formatMoney(session.originalPrice)}</small> : null}</div><div className="auction-rule-row"><span>最低加价 {formatMoney(session.minIncrement)}</span><span>{session.bidCount ?? 0} 次出价</span><span>{formatDateTime(session.endTime)} 结束</span></div>{leadingMine ? <div className="auction-my-state auction-my-state--leading"><Trophy aria-hidden="true" size={16} /><span><strong>你当前领先</strong>结束前仍可能被其他买家超过</span></div> : profile && session.currentAmount != null ? <div className="auction-my-state"><Gavel aria-hidden="true" size={16} /><span><strong>当前由其他买家领先</strong>下一口至少 {formatMoney(minimum)}</span></div> : null}
            {open ? <div className="auction-bid-box"><label><span>我的出价（元）</span><div className="money-input"><span>¥</span><input inputMode="decimal" min={(minimum / 100).toFixed(2)} step="0.01" type="number" placeholder={(minimum / 100).toFixed(2)} value={amounts[session.id] ?? ""} onChange={(event) => setAmounts((current) => ({ ...current, [session.id]: event.target.value }))} /></div></label><div className="auction-quick-bids"><button type="button" onClick={() => setQuickBid(session, 1)}>最低价</button><button type="button" onClick={() => setQuickBid(session, 2)}>+2 档</button><button type="button" onClick={() => setQuickBid(session, 5)}>+5 档</button></div><button className="button button--primary button--icon-label" type="button" onClick={() => beginBid(session)}><Gavel aria-hidden="true" size={16} />确认出价</button></div> : null}
            {session.status === 30 && winnerMine ? <div className="auction-winner"><Trophy aria-hidden="true" size={19} /><div><strong>恭喜中标</strong><span>请确认收货地址并创建待支付订单。</span></div><button className="button button--primary" type="button" onClick={() => void beginSettlement(session)}>立即结算</button></div> : null}
            {session.status === 30 && session.winnerUserId && !winnerMine ? <div className="auction-ended"><CheckCircle2 aria-hidden="true" size={17} />本场竞拍已结束</div> : null}
            {session.status === 50 ? <div className="auction-ended">本场结算未完成，商品库存已释放。</div> : null}
            <button aria-expanded={Boolean(historyOpen[session.id])} className="auction-history-toggle" type="button" onClick={() => void toggleHistory(session.id)}><History aria-hidden="true" size={15} />竞价记录（{historyTotals[session.id] ?? session.bidCount ?? 0}）<ChevronDown aria-hidden="true" size={15} /></button>
            {historyOpen[session.id] ? <div className="auction-history">{(histories[session.id] ?? []).length ? (histories[session.id] ?? []).map((bidItem) => <div className={bidItem.mine ? "auction-history__row auction-history__row--mine" : "auction-history__row"} key={bidItem.id}><span>{bidItem.bidderLabel}{bidItem.leading ? <b>领先</b> : null}</span><strong>{formatMoney(bidItem.amount)}</strong><small>{formatDateTime(bidItem.createTime)}</small></div>) : historyLoading[session.id] ? <span className="auction-history__empty">竞价记录加载中…</span> : <span className="auction-history__empty">暂无出价记录</span>}{(histories[session.id]?.length ?? 0) < (historyTotals[session.id] ?? 0) ? <button className="auction-history__more" disabled={historyLoading[session.id]} type="button" onClick={() => void loadHistory(session.id, (historyPages[session.id] ?? 1) + 1)}>{historyLoading[session.id] ? "加载中…" : `继续加载（剩余 ${(historyTotals[session.id] ?? 0) - (histories[session.id]?.length ?? 0)} 条）`}</button> : null}</div> : null}
          </div>
        </article>;
      })}</div> : null}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      <ConfirmDialog open={Boolean(pendingBid)} title="确认提交出价" description={pendingBid ? `你将为“${pendingBid.session.name}”出价 ${formatMoney(pendingBid.amount)}。出价成功后不能撤回。` : ""} confirmLabel="确认出价" busy={busy} onClose={() => setPendingBid(null)} onConfirm={() => void bid()} />
      <Drawer open={Boolean(settlement)} title="确认中标订单" description="选择地址后创建待支付订单。" busy={busy} onClose={() => setSettlement(null)}>{settlement ? <div className="drawer-form"><div className="purchase-summary"><strong>{settlement.session.productName || settlement.session.name}</strong><span>{settlement.session.skuLabel || "默认规格"}</span><b>{formatMoney(settlement.session.currentAmount)}</b></div><label className="field"><span>收货地址</span><select value={settlement.addressId} onChange={(event) => setSettlement({ ...settlement, addressId: Number(event.target.value) })}>{addresses.map((address) => <option key={address.id} value={address.id}>{address.defaultStatus ? "[默认] " : ""}{address.name} · {address.areaName ?? ""}{address.detailAddress}</option>)}</select></label><Link className="text-button button--icon-label" href="/addresses"><MapPin aria-hidden="true" size={14} />管理收货地址</Link><div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setSettlement(null)}>取消</button><button className="button button--primary" disabled={busy} type="button" onClick={() => void settle()}>{busy ? "创建中…" : "确认并创建订单"}</button></div></div> : null}</Drawer>
    </section>
  );
}
