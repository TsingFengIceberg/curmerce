"use client";

import { Eye, PackageSearch, Search, SlidersHorizontal } from "lucide-react";
import { FormEvent, Suspense, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CopyButton } from "@/components/copy-button";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { EntityPicker, type EntityOption } from "@/components/entity-picker";
import { adminOrderApi } from "@/lib/api/admin-order";
import { adminMerchantApi } from "@/lib/api/admin-merchant";
import { adminMemberApi } from "@/lib/api/admin-member";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatOrderStatus } from "@/lib/format";
import type { MerchantOrder } from "@/lib/types/api";
import { positiveInt, useUrlQuery } from "@/hooks/use-url-query";
import { TableDensityControl, useTableDensity } from "@/components/table-view-controls";

const PAGE_SIZE = 15;
type Filters = { orderNo: string; merchantId: string; memberUserId: string; dateFrom: string; dateTo: string };
const emptyFilters: Filters = { orderNo: "", merchantId: "", memberUserId: "", dateFrom: "", dateTo: "" };

function AdminOrdersPageContent() {
  const router = useRouter();
  const { searchParams, update } = useUrlQuery();
  const [orders, setOrders] = useState<MerchantOrder[]>([]);
  const [total, setTotal] = useState(0);
  const pageNo = positiveInt(searchParams.get("page"));
  const status = searchParams.get("status") ?? "";
  const filters: Filters = { orderNo: searchParams.get("orderNo") ?? "", merchantId: searchParams.get("merchantId") ?? "", memberUserId: searchParams.get("memberUserId") ?? "", dateFrom: searchParams.get("dateFrom") ?? "", dateTo: searchParams.get("dateTo") ?? "" };
  const sort = searchParams.get("sort") ?? "created-desc";
  const { density, setDensity } = useTableDensity("admin-orders");
  const [filterForm, setFilterForm] = useState<Filters>(filters);
  const [advancedOpen, setAdvancedOpen] = useState(Boolean(filters.merchantId || filters.memberUserId));
  const [detail, setDetail] = useState<MerchantOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAdminAccessToken()) { router.replace("/merchant/login"); return; }
    void load();
  }, [router, pageNo, status, filters.orderNo, filters.merchantId, filters.memberUserId, filters.dateFrom, filters.dateTo, sort]);

  useEffect(() => setFilterForm(filters), [filters.orderNo, filters.merchantId, filters.memberUserId, filters.dateFrom, filters.dateTo]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await adminOrderApi.page({ pageNo, pageSize: PAGE_SIZE, status: status ? Number(status) : undefined, orderNo: filters.orderNo || undefined, merchantId: filters.merchantId ? Number(filters.merchantId) : undefined, memberUserId: filters.memberUserId ? Number(filters.memberUserId) : undefined, dateFrom: filters.dateFrom || undefined, dateTo: filters.dateTo || undefined });
      setOrders([...(page.list ?? [])].sort((left, right) => sort === "amount-desc" ? right.payableAmount - left.payableAmount : sort === "created-asc" ? left.id - right.id : right.id - left.id));
      setTotal(page.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) { clearAdminToken(); router.replace("/merchant/login"); return; }
      setError(cause instanceof CurmerceApiError ? cause.message : "平台订单加载失败");
    } finally {
      setLoading(false);
    }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    update({ page: 1, orderNo: filterForm.orderNo.trim(), merchantId: filterForm.merchantId, memberUserId: filterForm.memberUserId, dateFrom: filterForm.dateFrom, dateTo: filterForm.dateTo });
  }

  function clearFilters() {
    setFilterForm(emptyFilters);
    update({ page: null, status: null, orderNo: null, merchantId: null, memberUserId: null, dateFrom: null, dateTo: null });
  }

  const searchMerchants = useCallback(async (keyword: string): Promise<EntityOption[]> => {
    const page = await adminMerchantApi.page({ pageNo: 1, pageSize: 8, name: keyword || undefined });
    return page.list.map((merchant) => ({ id: merchant.id, label: merchant.name, description: `${merchant.defaultStoreName} · ${merchant.contactMobile}` }));
  }, []);
  const resolveMerchant = useCallback(async (id: number): Promise<EntityOption | null> => {
    const merchant = await adminMerchantApi.get(id);
    return merchant ? { id: merchant.id, label: merchant.name, description: `${merchant.defaultStoreName} · ${merchant.contactMobile}` } : null;
  }, []);
  const searchMembers = useCallback(async (keyword: string): Promise<EntityOption[]> => {
    const page = await adminMemberApi.page({ pageNo: 1, pageSize: 8, keyword: keyword || undefined });
    return page.list.map((member) => ({ id: member.id, label: member.nickname || `用户 ${member.id}`, description: member.mobile || member.email }));
  }, []);
  const resolveMember = useCallback(async (id: number): Promise<EntityOption | null> => {
    const member = await adminMemberApi.get(id);
    return member ? { id: member.id, label: member.nickname || `用户 ${member.id}`, description: member.mobile || member.email } : null;
  }, []);

  const activeFilterCount = [status, filters.orderNo, filters.merchantId, filters.memberUserId, filters.dateFrom, filters.dateTo].filter(Boolean).length;

  return (
    <section className="content-section admin-page">
      <div className="section-heading"><div><p className="eyebrow">ADMIN · ORDERS</p><h1>平台订单</h1><p>跨商家查看交易状态、买家信息、商品和收货地址快照。</p></div></div>
      {error ? <Notice>{error}</Notice> : null}
      <div className="workspace-section admin-order-panel">
        <form className="platform-order-toolbar" onSubmit={search}><select aria-label="订单状态" value={status} onChange={(event) => update({ status: event.target.value, page: 1 })}><option value="">全部状态</option><option value="10">待支付</option><option value="20">待发货</option><option value="30">已发货</option><option value="40">已完成</option><option value="50">已取消</option></select><div className="order-search"><Search aria-hidden="true" size={16} /><input aria-label="订单号" placeholder="搜索订单号" value={filterForm.orderNo} onChange={(event) => setFilterForm((current) => ({ ...current, orderNo: event.target.value }))} /><button type="submit">查询</button></div><select aria-label="当前页排序" value={sort} onChange={(event) => update({ sort: event.target.value })}><option value="created-desc">最新下单</option><option value="created-asc">最早下单</option><option value="amount-desc">金额从高到低</option></select><TableDensityControl density={density} onChange={setDensity} /><button className={advancedOpen ? "button button--secondary button--small button--icon-label filter-toggle--active" : "button button--secondary button--small button--icon-label"} type="button" onClick={() => setAdvancedOpen((open) => !open)}><SlidersHorizontal aria-hidden="true" size={15} />更多筛选{activeFilterCount ? ` (${activeFilterCount})` : ""}</button><span>共 {total} 笔</span></form>
        {advancedOpen ? <form className="platform-order-advanced platform-order-advanced--entities" onSubmit={search}><EntityPicker label="商家" placeholder="搜索商家或店铺" value={filterForm.merchantId ? Number(filterForm.merchantId) : undefined} search={searchMerchants} resolve={resolveMerchant} onChange={(option) => setFilterForm((current) => ({ ...current, merchantId: option ? String(option.id) : "" }))} /><EntityPicker label="买家" placeholder="搜索昵称、手机号或邮箱" value={filterForm.memberUserId ? Number(filterForm.memberUserId) : undefined} search={searchMembers} resolve={resolveMember} onChange={(option) => setFilterForm((current) => ({ ...current, memberUserId: option ? String(option.id) : "" }))} /><label className="field field--compact"><span>开始日期</span><input type="date" value={filterForm.dateFrom} onChange={(event) => setFilterForm((current) => ({ ...current, dateFrom: event.target.value }))} /></label><label className="field field--compact"><span>结束日期</span><input min={filterForm.dateFrom} type="date" value={filterForm.dateTo} onChange={(event) => setFilterForm((current) => ({ ...current, dateTo: event.target.value }))} /></label><button className="button button--primary button--small" type="submit">应用筛选</button><button className="text-button" type="button" onClick={clearFilters}>清空全部</button></form> : null}
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !orders.length ? <EmptyState icon={<PackageSearch aria-hidden="true" size={23} />} title="没有符合条件的订单" description="调整状态、订单号或高级筛选条件后重新查询。" actionLabel={activeFilterCount ? "清空筛选" : undefined} onAction={activeFilterCount ? clearFilters : undefined} /> : null}
        {!loading && orders.length ? <div className="platform-order-table"><div className="platform-order-table__head"><span>订单</span><span>买家</span><span>商家 / 店铺</span><span>金额</span><span>状态</span><span>下单时间</span><span>操作</span></div>{orders.map((order) => <article className="platform-order-table__row" key={order.id}><div className="admin-order-identity"><strong>{order.orderNo}</strong><CopyButton value={order.orderNo} /><small>{order.itemCount} 件商品</small></div><div className="admin-table-stack"><strong>{order.buyerNickname || "买家"}</strong><small>{order.buyerMobile || order.buyerEmail || `用户 ${order.memberUserId}`}</small></div><div className="admin-table-stack"><strong>{order.merchantName || (order.sellerType === 2 ? "个人卖家" : `商家 ${order.merchantId ?? "—"}`)}</strong><small>{order.storeName || (order.sellerUserId ? `卖家 ${order.sellerUserId}` : `店铺 ${order.storeId ?? "—"}`)}</small></div><strong className="admin-order-amount">{formatMoney(order.payableAmount)}</strong><span className={`tag order-status order-status--${order.status}`}>{formatOrderStatus(order.status)}</span><span className="admin-table-time">{formatDateTime(order.createTime)}</span><button aria-label={`查看订单 ${order.orderNo}`} className="icon-button" title="查看详情" type="button" onClick={() => setDetail(order)}><Eye aria-hidden="true" size={16} /></button></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={(page) => update({ page })} />
      </div>

      <Drawer open={Boolean(detail)} title="平台订单详情" description={detail ? `订单 ${detail.orderNo}` : ""} onClose={() => setDetail(null)}>{detail ? <div className="drawer-form"><div className="detail-rows"><div><span>订单状态</span><strong>{formatOrderStatus(detail.status)}</strong></div><div><span>实付金额</span><strong>{formatMoney(detail.payableAmount)}</strong></div><div><span>买家</span><strong>{detail.buyerNickname || "买家"} · {detail.buyerMobile || detail.buyerEmail || `用户 ${detail.memberUserId}`}</strong></div><div><span>销售方</span><strong>{detail.merchantName || (detail.sellerType === 2 ? "个人卖家" : `商家 ${detail.merchantId ?? "—"}`)} · {detail.storeName || (detail.sellerUserId ? `卖家 ${detail.sellerUserId}` : `店铺 ${detail.storeId ?? "—"}`)}</strong></div><div><span>收货人</span><strong>{detail.receiverName || "—"} · {detail.receiverMobile || "—"}</strong></div><div><span>收货地址</span><strong>{detail.receiverAreaName ? `${detail.receiverAreaName} · ` : ""}{detail.receiverDetailAddress || "—"}</strong></div>{detail.shippingTime ? <div><span>发货时间</span><strong>{formatDateTime(detail.shippingTime)}</strong></div> : null}{detail.trackingNo ? <div><span>物流信息</span><strong>{detail.logisticsCompany || "—"} · {detail.trackingNo}</strong></div> : null}</div><div className="drawer-order-items"><h3>订单商品</h3>{detail.items.map((item) => <div key={item.id}><MediaImage alt={item.productName} fallback={<span className="listing-table__placeholder">C</span>} src={assetUrl(item.skuImageUrl || item.productImageUrl)} /><span><strong>{item.productName}</strong><small>{item.specificationValues?.map((value) => `${value.name}: ${value.value}`).join(" / ") || "默认规格"} · ×{item.quantity}</small></span><b>{formatMoney(item.totalAmount)}</b></div>)}</div></div> : null}</Drawer>
    </section>
  );
}

export default function AdminOrdersPage() {
  return <Suspense fallback={<div className="workspace-access-skeleton"><span /><span /><p>正在加载平台订单…</p></div>}><AdminOrdersPageContent /></Suspense>;
}
