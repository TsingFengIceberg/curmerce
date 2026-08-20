"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { CurmerceApiError, assetUrl } from "@/lib/api/client";
import { adminAuthApi } from "@/lib/api/admin-auth";
import { adminOrderApi } from "@/lib/api/admin-order";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney, formatOrderStatus } from "@/lib/format";
import type { MerchantOrder } from "@/lib/types/api";

export default function AdminOrdersPage() {
  const [orders, setOrders] = useState<MerchantOrder[]>([]);
  const [status, setStatus] = useState("");
  const [orderNo, setOrderNo] = useState("");
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (!getAdminAccessToken()) { window.location.href = "/merchant/login"; return; } void load(status, query); }, [status, query]);
  async function load(nextStatus = status, nextOrderNo = query) { setLoading(true); setError(null); try { const result = await adminOrderApi.page({ pageNo: 1, pageSize: 50, status: nextStatus ? Number(nextStatus) : undefined, orderNo: nextOrderNo || undefined }); setOrders(result?.list ?? []); } catch (cause) { if (cause instanceof CurmerceApiError && cause.status === 401) { clearAdminToken(); window.location.href = "/merchant/login"; return; } setError(cause instanceof CurmerceApiError ? cause.message : "平台订单加载失败"); } finally { setLoading(false); } }
  async function logout() { await adminAuthApi.logout(); window.location.href = "/merchant/login"; }
  return <section className="content-section admin-page"><div className="section-heading"><div><p className="eyebrow">ADMIN · ORDERS</p><h1>平台订单</h1><p>管理员只读查看全平台订单、买家和收货地址快照；发货仍由商家 Owner 执行。</p></div><div className="inline-actions"><Link className="button button--secondary" href="/admin/merchants">商家审核</Link><Link className="button button--secondary" href="/admin/refunds">退款审核</Link><button className="button button--secondary" type="button" onClick={() => void logout()}>退出后台</button></div></div>{error ? <Notice>{error}</Notice> : null}<div className="admin-toolbar"><select aria-label="订单状态" value={status} onChange={(event) => setStatus(event.target.value)}><option value="">全部状态</option><option value="10">待支付</option><option value="20">待发货</option><option value="30">已发货</option><option value="40">已完成</option><option value="50">已取消</option></select><form className="inline-actions" onSubmit={(event) => { event.preventDefault(); setQuery(orderNo.trim()); }}><input aria-label="订单号" placeholder="按订单号查询" value={orderNo} onChange={(event) => setOrderNo(event.target.value)} /><button className="button button--secondary" type="submit">查询</button></form></div><div className="orders-panel"><div className="panel-heading"><h2>订单列表</h2><span>{orders.length} 条</span></div>{loading ? <p className="empty-state">平台订单加载中…</p> : null}{!loading && orders.length === 0 ? <p className="empty-state">暂无订单。</p> : null}<div className="merchant-order-list">{orders.map((order) => <article className="merchant-order-card" key={order.id}><div className="merchant-order-card__header"><div><span className="order-card__date">{formatDateTime(order.createTime)}</span><strong>订单号：{order.orderNo}</strong></div><span className="tag order-status">{formatOrderStatus(order.status)}</span></div><div className="merchant-order-card__grid"><div><p className="eyebrow">BUYER</p><strong>{order.buyerNickname || "买家"}</strong><span>{order.buyerMobile || order.buyerEmail || `用户 ${order.memberUserId}`}</span></div><div><p className="eyebrow">MERCHANT / STORE</p><strong>{order.merchantId} / {order.storeId}</strong><span>{order.itemCount} 件商品</span></div><div><p className="eyebrow">AMOUNT</p><strong>{formatMoney(order.payableAmount)}</strong><span>{order.receiverName || "—"} · {order.receiverMobile || "—"}</span></div></div><p>{order.receiverAreaName ? `${order.receiverAreaName} · ` : ""}{order.receiverDetailAddress || "—"}</p><div className="merchant-item-list">{order.items?.map((item) => { const image = assetUrl(item.skuImageUrl || item.productImageUrl); return <div className="merchant-item" key={item.id}><div className="merchant-item__image">{image ? <img src={image} alt={item.productName} /> : <span>C</span>}</div><div><strong>{item.productName}</strong><span>×{item.quantity}</span></div><strong>{formatMoney(item.totalAmount)}</strong></div>; })}</div></article>)}</div></div></section>;
}
