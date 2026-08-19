"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Notice } from "@/components/notice";
import { cartApi } from "@/lib/api/cart";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { formatMoney } from "@/lib/format";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import type { CartItem, CartList } from "@/lib/types/api";

interface StoreGroup {
  storeId: number;
  storeName: string;
  items: CartItem[];
}

export default function CartPage() {
  const router = useRouter();
  const [cart, setCart] = useState<CartList>({ validList: [], invalidList: [] });
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void loadCart();
  }, [router]);

  async function loadCart() {
    setLoading(true);
    setError(null);
    try {
      const response = await cartApi.list();
      setCart({ validList: response?.validList ?? [], invalidList: response?.invalidList ?? [] });
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "购物车加载失败");
    } finally {
      setLoading(false);
    }
  }

  const groups = useMemo<StoreGroup[]>(() => {
    const map = new Map<number, StoreGroup>();
    for (const item of cart.validList) {
      const storeId = item.product?.storeId;
      if (!storeId) continue;
      const current = map.get(storeId) ?? { storeId, storeName: item.product?.storeName || "未命名店铺", items: [] };
      current.items.push(item);
      map.set(storeId, current);
    }
    return Array.from(map.values());
  }, [cart.validList]);

  async function updateQuantity(item: CartItem, quantity: number) {
    if (quantity < 1 || quantity > 99) return;
    setBusyId(item.id);
    setError(null);
    try {
      await cartApi.updateQuantity({ id: item.id, quantity });
      await loadCart();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "数量更新失败");
    } finally {
      setBusyId(null);
    }
  }

  async function setSelected(itemIds: number[], selected: boolean) {
    if (itemIds.length === 0) return;
    setBusyId(itemIds[0]);
    setError(null);
    try {
      await cartApi.updateSelected({ ids: itemIds, selected });
      await loadCart();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "购物车选择状态更新失败");
    } finally {
      setBusyId(null);
    }
  }

  async function removeItems(itemIds: number[]) {
    if (!window.confirm(`确定删除选中的 ${itemIds.length} 件购物车商品吗？`)) return;
    setBusyId(itemIds[0] ?? null);
    setError(null);
    try {
      await cartApi.delete(itemIds);
      setMessage("购物车商品已删除");
      await loadCart();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "购物车删除失败");
    } finally {
      setBusyId(null);
    }
  }

  async function checkout(group: StoreGroup) {
    const selected = group.items.filter((item) => item.selected);
    if (selected.length === 0) {
      setError("请先选择这个店铺中要结算的商品");
      return;
    }
    const otherStoreIds = cart.validList
      .filter((item) => item.selected && item.product?.storeId !== group.storeId)
      .map((item) => item.id);
    setError(null);
    setMessage(null);
    try {
      if (otherStoreIds.length > 0) {
        await cartApi.updateSelected({ ids: otherStoreIds, selected: false });
      }
      router.push(`/checkout?storeId=${group.storeId}`);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "准备结算失败");
    }
  }

  return (
    <section className="content-section cart-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">CART · ONE STORE CHECKOUT</p>
          <h1>购物车</h1>
          <p>按店铺组织商品；每次结算只会创建一个商家和店铺的订单。</p>
        </div>
        <Link className="button button--secondary" href="/catalog">继续逛逛 →</Link>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <p className="empty-state">购物车加载中…</p> : null}
      {!loading && groups.length === 0 && cart.invalidList.length === 0 ? <p className="empty-state cart-empty">购物车还是空的，去商品目录挑选一些喜欢的东西吧。</p> : null}
      {!loading ? (
        <div className="cart-layout">
          <div className="cart-groups">
            {groups.map((group) => {
              const selected = group.items.filter((item) => item.selected);
              const total = selected.reduce((sum, item) => sum + (item.sku?.price ?? 0) * item.quantity, 0);
              const allSelected = group.items.length > 0 && group.items.every((item) => item.selected);
              return (
                <section className="cart-store" key={group.storeId}>
                  <div className="cart-store__heading">
                    <div><p className="eyebrow">STORE</p><h2>{group.storeName}</h2></div>
                    <button className="text-button" type="button" onClick={() => void setSelected(group.items.map((item) => item.id), !allSelected)}>{allSelected ? "取消全选" : "全选本店"}</button>
                  </div>
                  <div className="cart-items">
                    {group.items.map((item) => (
                      <CartItemRow busy={busyId === item.id} item={item} key={item.id} onRemove={() => void removeItems([item.id])} onSelect={(selectedValue) => void setSelected([item.id], selectedValue)} onQuantity={(quantity) => void updateQuantity(item, quantity)} />
                    ))}
                  </div>
                  <div className="cart-store__footer">
                    <span>{selected.length} 件已选 · 合计 <strong>{formatMoney(total)}</strong></span>
                    <button className="button button--primary" disabled={selected.length === 0} type="button" onClick={() => void checkout(group)}>结算本店</button>
                  </div>
                </section>
              );
            })}
          </div>
          {cart.invalidList.length > 0 ? (
            <section className="cart-invalid">
              <div className="panel-heading"><h2>失效商品</h2><span>{cart.invalidList.length} 件</span></div>
              {cart.invalidList.map((item) => <CartItemRow busy={busyId === item.id} item={item} invalid key={item.id} onRemove={() => void removeItems([item.id])} onSelect={() => undefined} onQuantity={() => undefined} />)}
            </section>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function CartItemRow({
  item,
  busy,
  invalid = false,
  onSelect,
  onQuantity,
  onRemove,
}: {
  item: CartItem;
  busy: boolean;
  invalid?: boolean;
  onSelect: (selected: boolean) => void;
  onQuantity: (quantity: number) => void;
  onRemove: () => void;
}) {
  const image = assetUrl(item.sku?.imageUrl || item.product?.mainImageUrl);
  return (
    <article className={`cart-item${invalid ? " cart-item--invalid" : ""}`}>
      <input aria-label={`选择 ${item.product?.name ?? "商品"}`} checked={!invalid && item.selected} disabled={invalid || busy} type="checkbox" onChange={(event) => onSelect(event.target.checked)} />
      <div className="cart-item__image">{image ? <img src={image} alt={item.product?.name ?? "商品"} /> : <span>C</span>}</div>
      <div className="cart-item__main">
        <strong>{item.product?.name ?? "商品已不可用"}</strong>
        <span>{item.sku?.specificationValues?.map((value) => `${value.name}: ${value.value}`).join(" / ") || "默认规格"}</span>
        {invalid && item.invalidReason ? <small>{item.invalidReason}</small> : null}
      </div>
      <strong className="cart-item__price">{formatMoney(item.sku?.price)}</strong>
      {!invalid ? (
        <div className="quantity-control quantity-control--small">
          <button disabled={busy || item.quantity <= 1} type="button" onClick={() => onQuantity(item.quantity - 1)}>−</button>
          <span>{item.quantity}</span>
          <button disabled={busy || item.quantity >= 99} type="button" onClick={() => onQuantity(item.quantity + 1)}>＋</button>
        </div>
      ) : null}
      <button className="text-button text-button--danger" disabled={busy} type="button" onClick={onRemove}>删除</button>
    </article>
  );
}
