"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Notice } from "@/components/notice";
import { cartApi } from "@/lib/api/cart";
import { CurmerceApiError } from "@/lib/api/client";
import { memberApi } from "@/lib/api/member";
import { orderApi } from "@/lib/api/order";
import { formatMoney } from "@/lib/format";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import type { CartItem, CartList, MemberAddress, OrderCreateResult } from "@/lib/types/api";

export default function CheckoutPage() {
  const router = useRouter();
  const [cart, setCart] = useState<CartList>({ validList: [], invalidList: [] });
  const [addresses, setAddresses] = useState<MemberAddress[]>([]);
  const [groupKey, setGroupKey] = useState<string | null>(null);
  const [addressId, setAddressId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<OrderCreateResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void loadCheckout();
  }, [router]);

  async function loadCheckout() {
    setLoading(true);
    setError(null);
    try {
      const requested = new URLSearchParams(window.location.search).get("group");
      const [cartResponse, addressResponse] = await Promise.all([cartApi.list(), memberApi.listAddresses()]);
      let nextCart: CartList = { validList: cartResponse?.validList ?? [], invalidList: cartResponse?.invalidList ?? [] };
      const selectedItems = nextCart.validList.filter((item) => item.selected && sellerKey(item.product));
      const targetGroup = requested || sellerKey(selectedItems[0]?.product) || sellerKey(nextCart.validList[0]?.product);
      if (targetGroup) {
        const otherSelectedIds = selectedItems.filter((item) => sellerKey(item.product) !== targetGroup).map((item) => item.id);
        if (otherSelectedIds.length > 0) {
          await cartApi.updateSelected({ ids: otherSelectedIds, selected: false });
          const refreshed = await cartApi.list();
          nextCart = { validList: refreshed?.validList ?? [], invalidList: refreshed?.invalidList ?? [] };
        }
      }
      setCart(nextCart);
      setGroupKey(targetGroup ?? null);
      setAddresses(addressResponse ?? []);
      const defaultAddress = addressResponse?.find((address) => address.defaultStatus) ?? addressResponse?.[0];
      setAddressId(defaultAddress?.id ?? null);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "结算信息加载失败");
    } finally {
      setLoading(false);
    }
  }

  const items = useMemo(
    () => cart.validList.filter((item) => item.selected && sellerKey(item.product) === groupKey),
    [cart.validList, groupKey],
  );
  const total = items.reduce((sum, item) => sum + (item.sku?.price ?? 0) * item.quantity, 0);
  const storeName = items[0]?.product?.storeName ?? "当前店铺";

  async function submitOrder() {
    if (!addressId) {
      setError("请选择收货地址；如果还没有地址，请先添加一个地址。");
      return;
    }
    if (items.length === 0) {
      setError("当前没有可结算的商品，请回购物车重新选择。");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const idempotencyKey = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `checkout-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const response = await orderApi.create(addressId, idempotencyKey);
      setResult(response);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "订单创建失败");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="content-section checkout-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">CHECKOUT · IDEMPOTENT ORDER</p>
          <h1>确认订单</h1>
          <p>订单会保存收货地址快照，并以幂等键防止重复创建。</p>
        </div>
        <Link className="button button--secondary" href="/cart">返回购物车</Link>
      </div>
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <p className="empty-state">结算信息加载中…</p> : null}
      {!loading && result ? (
        <div className="order-success">
          <p className="eyebrow">ORDER CREATED</p>
          <h2>订单已创建</h2>
          <p>订单号：<strong>{result.orderNo}</strong></p>
          <p>待支付金额：<strong>{formatMoney(result.payableAmount)}</strong></p>
          <div className="hero-card__actions"><Link className="button button--primary" href={`/orders/${result.orderId}`}>去订单支付</Link><Link className="button button--secondary" href="/catalog">继续购物</Link><Link className="button button--secondary" href="/cart">查看购物车</Link></div>
        </div>
      ) : null}
      {!loading && !result ? (
        <div className="checkout-layout">
          <div className="checkout-main">
            <section className="checkout-panel">
              <div className="panel-heading"><h2>收货地址</h2><Link className="text-button" href="/addresses">管理地址</Link></div>
              {addresses.length === 0 ? <p className="empty-state">还没有地址，请先去添加收货地址。</p> : (
                <div className="checkout-addresses">
                  {addresses.map((address) => <AddressChoice address={address} checked={address.id === addressId} key={address.id} onSelect={() => setAddressId(address.id)} />)}
                </div>
              )}
            </section>
            <section className="checkout-panel">
              <div className="panel-heading"><h2>商品清单</h2><span>{storeName}</span></div>
              {items.length === 0 ? <p className="empty-state">当前没有选中的商品，请返回购物车选择。</p> : <div className="checkout-items">{items.map((item) => <CheckoutItem item={item} key={item.id} />)}</div>}
            </section>
          </div>
          <aside className="checkout-summary">
            <p className="eyebrow">SUMMARY</p>
            <h2>本店订单</h2>
            <div className="summary-row"><span>商品数量</span><strong>{items.reduce((sum, item) => sum + item.quantity, 0)}</strong></div>
            <div className="summary-row summary-row--total"><span>应付金额</span><strong>{formatMoney(total)}</strong></div>
            <button className="button button--primary button--full" disabled={submitting || items.length === 0 || !addressId} type="button" onClick={() => void submitOrder()}>{submitting ? "创建中…" : "提交订单"}</button>
            <p className="checkout-summary__hint">提交后订单进入待支付状态；重复点击不会创建重复订单。</p>
          </aside>
        </div>
      ) : null}
    </section>
  );
}

function sellerKey(product: CartItem["product"]): string | null {
  if (!product) return null;
  if (product.sellerType === 2 && product.sellerUserId) return `personal:${product.sellerUserId}`;
  if (product.sellerType === 1 && product.storeId) return `store:${product.storeId}`;
  return null;
}

function AddressChoice({ address, checked, onSelect }: { address: MemberAddress; checked: boolean; onSelect: () => void }) {
  return (
    <label className={`address-choice${checked ? " address-choice--active" : ""}`}>
      <input checked={checked} name="checkout-address" type="radio" onChange={onSelect} />
      <span><strong>{address.name}</strong> <small>{address.mobile}</small><br /><span>{address.areaName ? `${address.areaName} · ` : ""}{address.detailAddress}</span></span>
      {address.defaultStatus ? <em>默认</em> : null}
    </label>
  );
}

function CheckoutItem({ item }: { item: CartItem }) {
  return (
    <div className="checkout-item"><div><strong>{item.product?.name}</strong><span>{item.sku?.specificationValues?.map((value) => `${value.name}: ${value.value}`).join(" / ") || "默认规格"}</span></div><span>× {item.quantity}</span><strong>{formatMoney((item.sku?.price ?? 0) * item.quantity)}</strong></div>
  );
}
