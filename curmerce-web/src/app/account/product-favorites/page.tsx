"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Heart, Trash2 } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { ProductCard } from "@/components/product-card";
import { CurmerceApiError } from "@/lib/api/client";
import { productFavoriteApi } from "@/lib/api/product-favorite";
import { getAccessToken } from "@/lib/auth/storage";
import type { ProductFavorite } from "@/lib/types/api";

const PAGE_SIZE = 12;

export default function ProductFavoritesPage() {
  const router = useRouter();
  const [pageNo, setPageNo] = useState(1);
  const [items, setItems] = useState<ProductFavorite[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadFavorites = useCallback(async () => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await productFavoriteApi.page(pageNo, PAGE_SIZE);
      setItems(response?.list ?? []);
      setTotal(response?.total ?? 0);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品收藏加载失败");
    } finally {
      setLoading(false);
    }
  }, [pageNo, router]);

  useEffect(() => { void loadFavorites(); }, [loadFavorites]);

  async function removeFavorite(item: ProductFavorite) {
    setRemovingId(item.productId);
    setMessage(null);
    setError(null);
    try {
      await productFavoriteApi.set(item.productId, false);
      setMessage("已取消收藏");
      if (items.length === 1 && pageNo > 1) setPageNo((current) => current - 1);
      else await loadFavorites();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "取消收藏失败");
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <section className="content-section product-favorites-page">
      <header className="workspace-page-heading">
        <div><p className="eyebrow">SAVED PRODUCTS</p><h1>商品收藏</h1><p>集中查看感兴趣的商品，失效商品仍可从收藏中清理。</p></div>
        <Link className="button button--secondary button--small" href="/catalog">继续逛商品</Link>
      </header>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <div aria-label="商品收藏加载中" className="content-skeleton-grid"><span /><span /><span /><span /></div> : null}
      {!loading && items.length === 0 ? <EmptyState title="还没有收藏商品" description="浏览商品详情时，可以使用收藏按钮把感兴趣的商品留在这里。" icon={<Heart aria-hidden="true" size={23} />} action={{ href: "/catalog", label: "去发现商品" }} /> : null}
      {!loading && items.length > 0 ? (
        <div className="favorite-product-grid">
          {items.map((item) => (
            <div className="favorite-product-item" key={item.id}>
              {item.product ? <ProductCard product={item.product} /> : <div className="favorite-product-unavailable"><Heart aria-hidden="true" size={22} /><strong>商品已下架或不可见</strong><span>商品编号 {item.productId}</span></div>}
              <button aria-label={`取消收藏商品 ${item.product?.name ?? item.productId}`} className="favorite-product-remove" disabled={removingId === item.productId} type="button" onClick={() => void removeFavorite(item)}><Trash2 aria-hidden="true" size={15} />{removingId === item.productId ? "处理中…" : "取消收藏"}</button>
            </div>
          ))}
        </div>
      ) : null}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
    </section>
  );
}
