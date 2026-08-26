"use client";

import { Eye, PackageCheck, Search, Truck } from "lucide-react";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { CopyButton } from "@/components/copy-button";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { notifyWorkspaceBadgesChanged } from "@/components/workspace-shell";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { adminOrderApi } from "@/lib/api/admin-order";
import { ensureMerchantOwner } from "@/lib/auth/guards";
import { formatDateTime, formatMoney, formatOrderStatus } from "@/lib/format";
import type { MerchantOrder } from "@/lib/types/api";

const PAGE_SIZE = 15;
const LOGISTICS_COMPANIES = ["顺丰速运", "京东物流", "中通快递", "圆通速递", "申通快递", "韵达快递", "邮政 EMS", "其他"];
type ShippingRow = { order: MerchantOrder; trackingNo: string };

export default function MerchantOrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<MerchantOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [status, setStatus] = useState<number | undefined>();
  const [orderNo, setOrderNo] = useState("");
  const [orderNoInput, setOrderNoInput] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [detail, setDetail] = useState<MerchantOrder | null>(null);
  const [shippingRows, setShippingRows] = useState<ShippingRow[]>([]);
  const [logisticsCompany, setLogisticsCompany] = useState("顺丰速运");
  const [confirmShipping, setConfirmShipping] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void ensureMerchantOwner(router).then((allowed) => { if (allowed) void load(); });
  }, [router, pageNo, status, orderNo]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await adminOrderApi.pageOwn({ pageNo, pageSize: PAGE_SIZE, status, orderNo });
      setOrders(page.list ?? []);
      setTotal(page.total ?? 0);
      setSelectedIds((current) => new Set(Array.from(current).filter((id) => (page.list ?? []).some((order) => order.id === id && order.status === 20))));
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商家订单加载失败");
    } finally {
      setLoading(false);
    }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNo(1);
    setOrderNo(orderNoInput.trim());
  }

  function toggleSelected(order: MerchantOrder) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(order.id)) next.delete(order.id); else next.add(order.id);
      return next;
    });
  }

  function openShipping(nextOrders: MerchantOrder[]) {
    setShippingRows(nextOrders.map((order) => ({ order, trackingNo: order.trackingNo ?? "" })));
    setLogisticsCompany(nextOrders[0]?.logisticsCompany || "顺丰速运");
    setError(null);
  }

  function validateShipping() {
    if (!logisticsCompany.trim()) {
      setError("请选择物流公司");
      return false;
    }
    const invalid = shippingRows.find((row) => !/^[A-Za-z0-9-]{6,40}$/.test(row.trackingNo.trim()));
    if (invalid) {
      setError(`订单 ${invalid.order.orderNo} 的物流单号应为 6-40 位字母、数字或连字符`);
      return false;
    }
    return true;
  }

  async function ship() {
    if (!validateShipping()) return;
    setConfirmShipping(false);
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const results = await Promise.allSettled(shippingRows.map((row) => adminOrderApi.shipOwn({ id: row.order.id, logisticsCompany: logisticsCompany.trim(), trackingNo: row.trackingNo.trim() })));
      const failures = results.map((result, index) => ({ result, row: shippingRows[index] })).filter((item) => item.result.status === "rejected");
      if (failures.length) {
        setError(`${shippingRows.length - failures.length} 笔发货成功，${failures.length} 笔失败。请刷新后重试失败订单。`);
      } else {
        setMessage(shippingRows.length === 1 ? "订单已发货" : `${shippingRows.length} 笔订单已批量发货`);
      }
      setShippingRows([]);
      setSelectedIds(new Set());
      notifyWorkspaceBadgesChanged();
      await load();
    } finally {
      setBusy(false);
    }
  }

  const selectedOrders = useMemo(() => orders.filter((order) => selectedIds.has(order.id) && order.status === 20), [orders, selectedIds]);
  const pagePendingOrders = orders.filter((order) => order.status === 20);
  const allPendingSelected = pagePendingOrders.length > 0 && pagePendingOrders.every((order) => selectedIds.has(order.id));

  return (
    <section className="content-section merchant-orders-page">
      <div className="section-heading"><div><p className="eyebrow">MERCHANT · FULFILLMENT</p><h1>订单履约</h1><p>查询订单、查看商品与地址快照，并处理待发货任务。</p></div>{selectedOrders.length ? <button className="button button--primary button--icon-label" type="button" onClick={() => openShipping(selectedOrders)}><Truck aria-hidden="true" size={17} />批量发货（{selectedOrders.length}）</button> : null}</div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="workspace-section merchant-order-table-panel">
        <div className="order-admin-toolbar"><select aria-label="订单状态" value={status ?? ""} onChange={(event) => { setStatus(event.target.value ? Number(event.target.value) : undefined); setPageNo(1); }}><option value="">全部状态</option><option value="10">待支付</option><option value="20">待发货</option><option value="30">已发货</option><option value="40">已完成</option><option value="50">已取消</option></select><form className="order-search" onSubmit={search}><Search aria-hidden="true" size={16} /><input aria-label="订单号" placeholder="搜索订单号" value={orderNoInput} onChange={(event) => setOrderNoInput(event.target.value)} /><button type="submit">查询</button></form><span>共 {total} 笔</span></div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !orders.length ? <EmptyState icon={<PackageCheck aria-hidden="true" size={23} />} title="当前没有匹配的订单" description="调整状态或订单号后重新查询。" /> : null}
        {!loading && orders.length ? <div className="merchant-order-table"><div className="merchant-order-table__head"><span><input aria-label="选择当前页待发货订单" checked={allPendingSelected} disabled={!pagePendingOrders.length} type="checkbox" onChange={() => setSelectedIds(allPendingSelected ? new Set() : new Set(pagePendingOrders.map((order) => order.id)))} /></span><span>订单</span><span>买家</span><span>金额</span><span>状态</span><span>下单时间</span><span>操作</span></div>{orders.map((order) => <article className="merchant-order-table__row" key={order.id}><span><input aria-label={`选择订单 ${order.orderNo}`} checked={selectedIds.has(order.id)} disabled={order.status !== 20} type="checkbox" onChange={() => toggleSelected(order)} /></span><div className="merchant-order-table__identity"><strong>{order.orderNo}</strong><CopyButton value={order.orderNo} /><small>{order.itemCount} 件商品</small></div><div className="merchant-order-table__buyer"><strong>{order.buyerNickname || "买家"}</strong><span>{order.buyerMobile || order.buyerEmail || "—"}</span></div><strong>{formatMoney(order.payableAmount)}</strong><span className={`tag order-status order-status--${order.status}`}>{formatOrderStatus(order.status)}</span><span className="merchant-order-table__time">{formatDateTime(order.createTime)}</span><div className="listing-table__actions"><button aria-label={`查看订单 ${order.orderNo}`} className="icon-button" title="查看详情" type="button" onClick={() => setDetail(order)}><Eye aria-hidden="true" size={16} /></button>{order.status === 20 ? <button aria-label={`发货订单 ${order.orderNo}`} className="icon-button" title="填写物流并发货" type="button" onClick={() => openShipping([order])}><Truck aria-hidden="true" size={16} /></button> : null}</div></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>
      <Drawer open={Boolean(detail)} title="订单详情" description={detail ? `订单 ${detail.orderNo}` : ""} onClose={() => setDetail(null)}>{detail ? <div className="drawer-form"><div className="detail-rows"><div><span>订单状态</span><strong>{formatOrderStatus(detail.status)}</strong></div><div><span>实付金额</span><strong>{formatMoney(detail.payableAmount)}</strong></div><div><span>买家</span><strong>{detail.buyerNickname || "买家"} · {detail.buyerMobile || detail.buyerEmail || "—"}</strong></div><div><span>收货人</span><strong>{detail.receiverName || "—"} · {detail.receiverMobile || "—"}</strong></div><div><span>收货地址</span><strong>{detail.receiverAreaName ? `${detail.receiverAreaName} · ` : ""}{detail.receiverDetailAddress || "—"}</strong></div>{detail.status >= 30 ? <div><span>物流信息</span><strong>{detail.logisticsCompany || "—"} · {detail.trackingNo || "—"}</strong></div> : null}</div><div className="drawer-order-items"><h3>订单商品</h3>{detail.items.map((item) => <div key={item.id}><MediaImage alt={item.productName} fallback={<span className="listing-table__placeholder">C</span>} src={assetUrl(item.skuImageUrl || item.productImageUrl)} /><span><strong>{item.productName}</strong><small>{item.specificationValues?.map((value) => `${value.name}: ${value.value}`).join(" / ") || "默认规格"} · ×{item.quantity}</small></span><b>{formatMoney(item.totalAmount)}</b></div>)}</div>{detail.status === 20 ? <button className="button button--primary button--icon-label" type="button" onClick={() => { const order = detail; setDetail(null); openShipping([order]); }}><Truck aria-hidden="true" size={16} />填写物流并发货</button> : null}</div> : null}</Drawer>
      <Drawer open={shippingRows.length > 0} title={shippingRows.length > 1 ? "批量发货" : "订单发货"} description={`将处理 ${shippingRows.length} 笔待发货订单。`} busy={busy} onClose={() => setShippingRows([])}><div className="drawer-form"><label className="field"><span>物流公司</span><select value={logisticsCompany} onChange={(event) => setLogisticsCompany(event.target.value)}>{LOGISTICS_COMPANIES.map((company) => <option key={company}>{company}</option>)}</select></label><div className="batch-shipping-list">{shippingRows.map((row, index) => <label className="field" key={row.order.id}><span>{row.order.orderNo} · {row.order.receiverName || "收货人"}</span><input maxLength={40} placeholder="填写物流单号" value={row.trackingNo} onChange={(event) => setShippingRows((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, trackingNo: event.target.value } : item))} /></label>)}</div><div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setShippingRows([])}>取消</button><button className="button button--primary button--icon-label" disabled={busy} type="button" onClick={() => { if (validateShipping()) setConfirmShipping(true); }}><Truck aria-hidden="true" size={16} />提交发货</button></div></div></Drawer>
      <ConfirmDialog open={confirmShipping} title={shippingRows.length > 1 ? "确认批量发货" : "确认订单发货"} description={`确认使用“${logisticsCompany}”提交 ${shippingRows.length} 笔物流信息吗？提交后订单状态将变为已发货。`} confirmLabel="确认发货" busy={busy} onClose={() => setConfirmShipping(false)} onConfirm={() => void ship()} />
    </section>
  );
}
