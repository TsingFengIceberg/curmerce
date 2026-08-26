"use client";

import { ClipboardCheck, Download, History, Pencil, Plus, Store, Upload } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { ProductOperationHistory } from "@/components/product-operation-history";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { personalApi } from "@/lib/api/personal";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { PersonalListing } from "@/lib/types/api";

const PAGE_SIZE = 12;
const auditLabels: Record<number, string> = { 0: "草稿", 1: "待审核", 2: "审核通过", 3: "已驳回" };
const saleLabels: Record<number, string> = { 0: "下架", 1: "上架" };
type TransitionAction = "submit" | "list" | "delist";

export default function PersonalListingsPage() {
  const router = useRouter();
  const [listings, setListings] = useState<PersonalListing[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [auditStatus, setAuditStatus] = useState("");
  const [saleStatus, setSaleStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState<{ listing: PersonalListing; action: TransitionAction } | null>(null);
  const [historyTarget, setHistoryTarget] = useState<PersonalListing | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void loadListings();
  }, [router, pageNo, auditStatus, saleStatus]);

  async function loadListings() {
    setLoading(true);
    setError(null);
    try {
      const response = await personalApi.page({ pageNo, pageSize: PAGE_SIZE, auditStatus: auditStatus ? Number(auditStatus) : undefined, saleStatus: saleStatus ? Number(saleStatus) : undefined });
      setListings(response?.list ?? []);
      setTotal(response?.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "个人商品加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function transition() {
    if (!pending) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (pending.action === "submit") await personalApi.submit(pending.listing.id);
      if (pending.action === "list") await personalApi.list(pending.listing.id);
      if (pending.action === "delist") await personalApi.delist(pending.listing.id);
      setMessage(pending.action === "submit" ? "商品已提交审核" : pending.action === "list" ? "商品已上架" : "商品已下架");
      setPending(null);
      await loadListings();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品状态更新失败");
    } finally {
      setBusy(false);
    }
  }

  const actionText = pending?.action === "submit"
    ? { title: "提交商品审核", description: "提交后商品内容将暂时不能修改，平台审核通过后才能上架。", label: "提交审核" }
    : pending?.action === "list"
      ? { title: "上架商品", description: "上架后买家即可在商城中看到并购买这件闲置商品。", label: "确认上架" }
      : { title: "下架商品", description: "下架后商品将立即停止出售，已有订单不会受到影响。", label: "确认下架" };

  return (
    <section className="content-section admin-page">
      <div className="section-heading"><div><p className="eyebrow">PERSONAL SELLER · LISTINGS</p><h1>闲置商品</h1><p>分别管理草稿、审核和销售状态，一件商品始终只对应一件库存。</p></div><Link className="button button--primary button--icon-label" href="/personal/listings/new"><Plus aria-hidden="true" size={17} />发布闲置</Link></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="workspace-section listing-table-panel">
        <div className="listing-toolbar"><div><strong>我的商品</strong><span>共 {total} 件</span></div><div className="inline-actions"><select aria-label="审核状态" value={auditStatus} onChange={(event) => { setAuditStatus(event.target.value); setPageNo(1); }}><option value="">全部审核状态</option><option value="0">草稿</option><option value="1">待审核</option><option value="2">审核通过</option><option value="3">已驳回</option></select><select aria-label="销售状态" value={saleStatus} onChange={(event) => { setSaleStatus(event.target.value); setPageNo(1); }}><option value="">全部销售状态</option><option value="0">下架</option><option value="1">上架</option></select></div></div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !listings.length ? <EmptyState icon={<Store aria-hidden="true" size={23} />} title="还没有符合条件的闲置商品" description="发布第一件闲置商品，保存草稿后即可提交审核。" action={{ href: "/personal/listings/new", label: "发布第一件商品" }} /> : null}
        {!loading && listings.length ? <div className="listing-table"><div className="listing-table__head"><span>商品</span><span>价格与库存</span><span>状态</span><span>最近操作</span><span>操作</span></div>{listings.map((listing) => <article className="listing-table__row" key={listing.id}><div className="listing-table__product"><MediaImage alt={listing.name} fallback={<span className="listing-table__placeholder">C</span>} src={assetUrl(listing.mainImageUrl)} /><div><strong>{listing.name}</strong><small>{listing.condition}</small>{listing.rejectReason ? <em>驳回：{listing.rejectReason}</em> : null}</div></div><div className="listing-table__value"><strong>{formatMoney(listing.price)}</strong><span>{listing.stock > 0 ? "1 件可售" : "已售出"}</span></div><div className="listing-table__status"><span className="tag">{auditLabels[listing.auditStatus] ?? `审核 ${listing.auditStatus}`}</span><span className="tag">{saleLabels[listing.saleStatus] ?? `销售 ${listing.saleStatus}`}</span></div><div className="listing-table__updated"><span>{formatDateTime(listing.updateTime ?? listing.createTime)}</span><small>商品资料或状态更新</small></div><div className="listing-table__actions"><button aria-label={`查看 ${listing.name} 操作记录`} className="icon-button" title="操作记录" type="button" onClick={() => setHistoryTarget(listing)}><History aria-hidden="true" size={16} /></button>{listing.auditStatus === 0 || listing.auditStatus === 3 ? <Link aria-label={`编辑 ${listing.name}`} className="icon-button" href={`/personal/listings/${listing.id}/edit`} title="编辑草稿"><Pencil aria-hidden="true" size={16} /></Link> : null}{listing.auditStatus === 0 || listing.auditStatus === 3 ? <button aria-label={`提交 ${listing.name} 审核`} className="icon-button" title="提交审核" type="button" onClick={() => setPending({ listing, action: "submit" })}><ClipboardCheck aria-hidden="true" size={16} /></button> : null}{listing.auditStatus === 2 && listing.saleStatus === 0 && listing.stock > 0 ? <button aria-label={`上架 ${listing.name}`} className="icon-button" title="上架" type="button" onClick={() => setPending({ listing, action: "list" })}><Upload aria-hidden="true" size={16} /></button> : null}{listing.saleStatus === 1 ? <button aria-label={`下架 ${listing.name}`} className="icon-button icon-button--danger" title="下架" type="button" onClick={() => setPending({ listing, action: "delist" })}><Download aria-hidden="true" size={16} /></button> : null}</div></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>
      <ConfirmDialog open={Boolean(pending)} title={actionText.title} description={actionText.description} confirmLabel={actionText.label} dangerous={pending?.action === "delist"} busy={busy} onClose={() => { if (!busy) setPending(null); }} onConfirm={() => void transition()} />
      <ProductOperationHistory open={Boolean(historyTarget)} productId={historyTarget?.id} productName={historyTarget?.name} scope="personal" onClose={() => setHistoryTarget(null)} />
    </section>
  );
}
