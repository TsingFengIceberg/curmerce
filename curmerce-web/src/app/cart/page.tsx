"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { EmptyState } from "@/components/empty-state";
import { Trash2 } from "lucide-react";
import { cartApi } from "@/lib/api/cart";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { formatMoney } from "@/lib/format";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { currentLocation, loginPath } from "@/lib/auth/guards";
import type { CartItem, CartList } from "@/lib/types/api";
import { notifyFeedback } from "@/components/feedback-center";
import { notifyCartChanged } from "@/lib/ui-events";

interface StoreGroup {
  key: string;
  storeId: number | null;
  storeName: string;
  items: CartItem[];
}

type PendingAction = { type: "remove"; ids: number[] } | { type: "checkout"; group: StoreGroup; otherIds: number[] } | null;

export default function CartPage() {
  const router = useRouter();
  const [cart, setCart] = useState<CartList>({ validList: [], invalidList: [] });
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace(loginPath("/login", currentLocation()));
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
        router.replace(loginPath("/login", currentLocation()));
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "购物车加载失败");
    } finally {
      setLoading(false);
    }
  }

  const groups = useMemo<StoreGroup[]>(() => {
    const map = new Map<string, StoreGroup>();
    for (const item of cart.validList) {
      const product = item.product;
      const key = sellerKey(product);
      if (!key) continue;
      const storeId = product?.storeId ?? null;
      const current = map.get(key) ?? { key, storeId, storeName: product?.storeName || "个人卖家", items: [] };
      current.items.push(item);
      map.set(key, current);
    }
    return Array.from(map.values());
  }, [cart.validList]);

  async function updateQuantity(item: CartItem, quantity: number) {
    if (quantity < 1 || quantity > 99) return;
    setBusyId(item.id);
    setError(null);
    const previousQuantity = item.quantity;
    setCart((current) => ({ ...current, validList: current.validList.map((entry) => entry.id === item.id ? { ...entry, quantity } : entry) }));
    try {
      await cartApi.updateQuantity({ id: item.id, quantity });
      notifyCartChanged();
    } catch (cause) {
      setCart((current) => ({ ...current, validList: current.validList.map((entry) => entry.id === item.id ? { ...entry, quantity: previousQuantity } : entry) }));
      setError(cause instanceof CurmerceApiError ? cause.message : "数量更新失败");
      notifyFeedback({ tone: "error", title: "数量更新失败", description: cause instanceof Error ? cause.message : undefined, actionLabel: "重试", onAction: () => updateQuantity(item, quantity) });
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
    const removed = [...cart.validList, ...cart.invalidList].filter((item) => itemIds.includes(item.id));
    setBusyId(itemIds[0] ?? null);
    setError(null);
    setCart((current) => ({ validList: current.validList.filter((item) => !itemIds.includes(item.id)), invalidList: current.invalidList.filter((item) => !itemIds.includes(item.id)) }));
    try {
      await cartApi.delete(itemIds);
      setMessage("购物车商品已删除");
      notifyCartChanged();
      const canRestore = removed.some((item) => item.sku?.id);
      notifyFeedback({ tone: "success", title: `已删除 ${removed.length} 件购物车商品`, actionLabel: canRestore ? "撤销" : undefined, onAction: canRestore ? async () => { for (const item of removed) if (item.sku?.id) await cartApi.add({ skuId: item.sku.id, quantity: item.quantity }); await loadCart(); notifyCartChanged(); } : undefined });
    } catch (cause) {
      await loadCart();
      setError(cause instanceof CurmerceApiError ? cause.message : "购物车删除失败");
      notifyFeedback({ tone: "error", title: "购物车删除失败", description: cause instanceof Error ? cause.message : undefined, actionLabel: "重试", onAction: () => removeItems(itemIds) });
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
      .filter((item) => item.selected && sellerKey(item.product) !== group.key)
      .map((item) => item.id);
    if (otherStoreIds.length > 0) {
      setPendingAction({ type: "checkout", group, otherIds: otherStoreIds });
      return;
    }
    router.push(`/checkout?group=${encodeURIComponent(group.key)}`);
  }

  async function prepareCheckout(group: StoreGroup, otherStoreIds: number[]) {
    setError(null);
    setMessage(null);
    try {
      await cartApi.updateSelected({ ids: otherStoreIds, selected: false });
      router.push(`/checkout?group=${encodeURIComponent(group.key)}`);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "准备结算失败");
    }
  }

  async function confirmPendingAction() {
    const action = pendingAction;
    if (!action) return;
    setPendingAction(null);
    if (action.type === "remove") await removeItems(action.ids);
    else await prepareCheckout(action.group, action.otherIds);
  }

  const selectedIds = cart.validList.filter((item) => item.selected).map((item) => item.id);

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
      {!loading && groups.length === 0 && cart.invalidList.length === 0 ? <EmptyState title="购物车还是空的" description="从商品目录或社区分享中挑选喜欢的东西。" action={{ href: "/catalog", label: "去逛商城" }} /> : null}
      {!loading ? (
        <div className="cart-layout">
          <div className="cart-groups">
            {selectedIds.length > 0 ? <div className="cart-batch-toolbar"><span>已选择 {selectedIds.length} 件商品</span><button className="text-button text-button--danger" type="button" onClick={() => setPendingAction({ type: "remove", ids: selectedIds })}><Trash2 aria-hidden="true" size={15} />删除选中</button></div> : null}
            {groups.map((group) => {
              const selected = group.items.filter((item) => item.selected);
              const total = selected.reduce((sum, item) => sum + (item.sku?.price ?? 0) * item.quantity, 0);
              const allSelected = group.items.length > 0 && group.items.every((item) => item.selected);
              return (
                <section className="cart-store" key={group.key}>
                  <div className="cart-store__heading">
                    <div><p className="eyebrow">STORE</p><h2>{group.storeName}</h2></div>
                    <button className="text-button" type="button" onClick={() => void setSelected(group.items.map((item) => item.id), !allSelected)}>{allSelected ? "取消全选" : "全选本店"}</button>
                  </div>
                  <div className="cart-items">
                    {group.items.map((item) => (
                      <CartItemRow busy={busyId === item.id} item={item} key={item.id} onRemove={() => setPendingAction({ type: "remove", ids: [item.id] })} onSelect={(selectedValue) => void setSelected([item.id], selectedValue)} onQuantity={(quantity) => void updateQuantity(item, quantity)} />
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
              <div className="panel-heading"><h2>失效商品</h2><button className="text-button text-button--danger" type="button" onClick={() => setPendingAction({ type: "remove", ids: cart.invalidList.map((item) => item.id) })}>清空 {cart.invalidList.length} 件</button></div>
              {cart.invalidList.map((item) => <CartItemRow busy={busyId === item.id} item={item} invalid key={item.id} onRemove={() => setPendingAction({ type: "remove", ids: [item.id] })} onSelect={() => undefined} onQuantity={() => undefined} />)}
            </section>
          ) : null}
        </div>
      ) : null}
      <ConfirmDialog open={pendingAction !== null} dangerous={pendingAction?.type === "remove"} title={pendingAction?.type === "checkout" ? "只结算当前店铺？" : "删除购物车商品？"} description={pendingAction?.type === "checkout" ? `其他店铺已选中的 ${pendingAction.otherIds.length} 件商品会保留在购物车，但本次将取消勾选。` : `将从购物车移除 ${pendingAction?.ids.length ?? 0} 件商品，此操作不能撤销。`} confirmLabel={pendingAction?.type === "checkout" ? "继续结算" : "确认删除"} onClose={() => setPendingAction(null)} onConfirm={() => void confirmPendingAction()} />
    </section>
  );
}

function sellerKey(product: CartItem["product"]): string | null {
  if (!product) return null;
  if (product.sellerType === 2 && product.sellerUserId) return `personal:${product.sellerUserId}`;
  if (product.sellerType === 1 && product.storeId) return `store:${product.storeId}`;
  return null;
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
      <Link className="cart-item__image" href={item.product?.id ? `/products/${item.product.id}` : "/catalog"}><MediaImage alt={item.product?.name ?? "商品"} fallback={<span>C</span>} src={image} /></Link>
      <div className="cart-item__main">
        {item.product?.id ? <Link href={`/products/${item.product.id}`}><strong>{item.product.name}</strong></Link> : <strong>商品已不可用</strong>}
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
