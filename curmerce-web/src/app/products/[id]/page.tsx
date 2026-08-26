"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Notice } from "@/components/notice";
import { ProductCard } from "@/components/product-card";
import { EmptyState } from "@/components/empty-state";
import { communityApi } from "@/lib/api/community";
import { catalogApi } from "@/lib/api/catalog";
import { cartApi } from "@/lib/api/cart";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { formatMoney, formatStock } from "@/lib/format";
import { productFavoriteApi } from "@/lib/api/product-favorite";
import { getAccessToken } from "@/lib/auth/storage";
import type { CommunityPost, PublicProductDetail, PublicProductSku, PublicProductSummary } from "@/lib/types/api";
import { BadgeCheck, Heart, RotateCcw, ShieldCheck, Store, Truck } from "lucide-react";

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [product, setProduct] = useState<PublicProductDetail | null>(null);
  const [selectedSkuId, setSelectedSkuId] = useState<number | null>(null);
  const [selectedImage, setSelectedImage] = useState<string | null>(null);
  const [relatedProducts, setRelatedProducts] = useState<PublicProductSummary[]>([]);
  const [relatedPosts, setRelatedPosts] = useState<CommunityPost[]>([]);
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [favorite, setFavorite] = useState(false);
  const [favoritePending, setFavoritePending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const id = Number(params.id);
    if (!Number.isInteger(id) || id < 1) {
      setError("商品编号不正确");
      setLoading(false);
      return;
    }
    void loadProduct(id);
  }, [params.id]);

  async function loadProduct(id: number) {
    setLoading(true);
    setError(null);
    try {
      const response = await catalogApi.productDetail(id);
      setProduct(response);
      if (getAccessToken()) {
        try { setFavorite(await productFavoriteApi.status(id)); } catch { setFavorite(false); }
      } else {
        setFavorite(false);
      }
      const firstAvailable = response?.skus?.find((sku) => sku.available && sku.stock > 0) ?? response?.skus?.[0];
      setSelectedSkuId(firstAvailable?.id ?? null);
      const [productPage, postPage] = await Promise.all([
        catalogApi.productPage({ pageNo: 1, pageSize: 5, categoryId: response.categoryId }),
        communityApi.page({ pageNo: 1, pageSize: 3, productId: response.id }),
      ]);
      setRelatedProducts((productPage?.list ?? []).filter((item) => item.id !== response.id).slice(0, 4));
      setRelatedPosts(postPage?.list ?? []);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品详情加载失败");
    } finally {
      setLoading(false);
    }
  }

  const selectedSku = useMemo<PublicProductSku | null>(
    () => product?.skus?.find((sku) => sku.id === selectedSkuId) ?? null,
    [product, selectedSkuId],
  );
  const currentImage = assetUrl(selectedImage || selectedSku?.imageUrl || product?.imageUrls?.[0] || product?.mainImageUrl);
  const currentPrice = selectedSku?.price ?? product?.minPrice ?? 0;
  const maxQuantity = Math.max(1, Math.min(selectedSku?.stock ?? product?.totalStock ?? 1, 99));
  const specificationGroups = useMemo(() => {
    const groups = new Map<string, string[]>();
    for (const sku of product?.skus ?? []) for (const spec of sku.specificationValues ?? []) {
      const values = groups.get(spec.name) ?? [];
      if (!values.includes(spec.value)) values.push(spec.value);
      groups.set(spec.name, values);
    }
    return Array.from(groups, ([name, values]) => ({ name, values }));
  }, [product]);
  const gallery = useMemo(() => Array.from(new Set([product?.mainImageUrl, ...(product?.imageUrls ?? []), selectedSku?.imageUrl].filter((value): value is string => Boolean(value)))), [product, selectedSku]);

  function selectSpecification(name: string, value: string) {
    if (!product) return;
    const current = new Map(selectedSku?.specificationValues?.map((item) => [item.name, item.value]) ?? []);
    current.set(name, value);
    const candidates = product.skus.filter((sku) => sku.available && sku.stock > 0 && sku.specificationValues?.some((item) => item.name === name && item.value === value));
    const next = candidates.sort((left, right) => {
      const score = (sku: PublicProductSku) => sku.specificationValues?.filter((item) => item.name === name || current.get(item.name) === item.value).length ?? 0;
      return score(right) - score(left);
    })[0];
    setSelectedSkuId(next?.id ?? null); setSelectedImage(null); setQuantity(1);
  }

  async function addToCart() {
    if (!selectedSku || !selectedSku.available || selectedSku.stock < 1) {
      setError("请选择有库存的商品规格");
      return;
    }
    setAdding(true);
    setMessage(null);
    setError(null);
    try {
      await cartApi.add({ skuId: selectedSku.id, quantity });
      setMessage("已加入购物车");
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        router.push("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "加入购物车失败");
    } finally {
      setAdding(false);
    }
  }

  async function toggleFavorite() {
    if (!product) return;
    if (!getAccessToken()) {
      router.push("/login");
      return;
    }
    setFavoritePending(true);
    setMessage(null);
    setError(null);
    try {
      const next = !favorite;
      await productFavoriteApi.set(product.id, next);
      setFavorite(next);
      setMessage(next ? "已收藏商品" : "已取消收藏");
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "收藏状态更新失败");
    } finally {
      setFavoritePending(false);
    }
  }

  if (loading) return <p className="empty-state">商品详情加载中…</p>;
  if (!product) return <Notice>{error ?? "商品不存在或已下架"}</Notice>;

  return (
    <section className="content-section product-detail-page">
      <div className="product-detail__back"><Link href="/catalog">← 返回商品目录</Link><Link href="/cart">购物车 →</Link></div>
      {message ? <Notice tone="success">{message}，<Link href="/cart">去购物车查看</Link></Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="product-detail">
        <div className="product-detail__visual">
          <div className="product-detail__main-image">
            {currentImage ? <img src={currentImage} alt={product.name} /> : <span>CURMERCE</span>}
          </div>
          {gallery.length > 1 ? (
            <div className="product-detail__thumbnails">
              {gallery.map((image) => {
                const source = assetUrl(image);
                return source ? <button className={source === currentImage ? "product-thumbnail product-thumbnail--active" : "product-thumbnail"} key={image} type="button" onClick={() => setSelectedImage(image)}><img src={source} alt="切换商品展示图" /></button> : null;
              })}
            </div>
          ) : null}
        </div>
        <div className="product-detail__info">
          <Link className="product-seller" href={`/catalog?sellerType=${product.sellerType ?? 1}${product.sellerType === 1 && product.storeName ? `&store=${encodeURIComponent(product.storeName)}` : ""}`}><Store aria-hidden="true" size={16} /><span><small>{product.sellerType === 2 ? "个人卖家" : "商家店铺"}</small><strong>{product.sellerName || product.storeName || "Curmerce 卖家"}</strong></span></Link>
          <h1>{product.name}</h1>
          {product.subtitle ? <p className="product-detail__subtitle">{product.subtitle}</p> : null}
          <div className="product-detail__price">{formatMoney(currentPrice)}</div>
          {product.minMarketPrice && product.minMarketPrice > currentPrice ? <del>{formatMoney(product.minMarketPrice)}</del> : null}
          <p className="product-detail__stock">{formatStock(selectedSku?.stock ?? product.totalStock)}</p>
          {specificationGroups.length > 0 ? (
            <div className="sku-selector">
              {specificationGroups.map((group) => <div className="sku-spec-group" key={group.name}><div className="product-detail__label">{group.name}</div><div className="sku-options">{group.values.map((value) => { const active = selectedSku?.specificationValues?.some((item) => item.name === group.name && item.value === value); const available = product.skus.some((sku) => sku.available && sku.stock > 0 && sku.specificationValues?.some((item) => item.name === group.name && item.value === value)); return <button className={`sku-option${active ? " sku-option--active" : ""}`} disabled={!available} key={value} type="button" onClick={() => selectSpecification(group.name, value)}>{value}</button>; })}</div></div>)}
            </div>
          ) : null}
          <div className="quantity-row">
            <span className="product-detail__label">数量</span>
            <div className="quantity-control">
              <button disabled={quantity <= 1} type="button" onClick={() => setQuantity((current) => Math.max(1, current - 1))}>−</button>
              <span>{quantity}</span>
              <button disabled={quantity >= maxQuantity} type="button" onClick={() => setQuantity((current) => Math.min(maxQuantity, current + 1))}>＋</button>
            </div>
          </div>
          <div className="product-detail__actions">
            <button className="button button--primary product-detail__add" disabled={adding || !selectedSku?.available} type="button" onClick={() => void addToCart()}>{adding ? "加入中…" : "加入购物车"}</button>
            <button aria-pressed={favorite} className={favorite ? "button button--secondary product-detail__favorite product-detail__favorite--active" : "button button--secondary product-detail__favorite"} disabled={favoritePending} type="button" onClick={() => void toggleFavorite()}><Heart aria-hidden="true" fill={favorite ? "currentColor" : "none"} size={18} />{favoritePending ? "处理中…" : favorite ? "已收藏" : "收藏"}</button>
          </div>
          <div className="product-assurances"><span><ShieldCheck aria-hidden="true" size={17} />平台交易保障</span><span><Truck aria-hidden="true" size={17} />卖家负责发货</span><span><RotateCcw aria-hidden="true" size={17} />支持基础售后</span></div>
        </div>
      </div>
      <article className="product-description">
        <p className="eyebrow">PRODUCT NOTE</p>
        <h2>商品说明</h2>
        <div>{product.description || "商家暂未补充商品说明。"}</div>
      </article>
      {relatedPosts.length > 0 ? <section className="product-related-section"><div className="discovery-section__heading"><div><p className="eyebrow">COMMUNITY EXPERIENCE</p><h2>社区里有人这样聊到它</h2></div></div><div className="product-related-posts">{relatedPosts.map((post) => <Link href={`/community/${post.id}`} key={post.id}><BadgeCheck aria-hidden="true" size={18} /><div><strong>{post.title}</strong><span>{post.authorNickname || "Curmerce 用户"} · {post.commentCount} 条评论</span></div></Link>)}</div></section> : null}
      <section className="product-related-section"><div className="discovery-section__heading"><div><p className="eyebrow">MORE TO DISCOVER</p><h2>同类商品</h2></div><Link href={`/catalog?category=${product.categoryId}`}>查看当前分类</Link></div>{relatedProducts.length ? <div className="product-grid home-product-grid">{relatedProducts.map((item) => <ProductCard key={item.id} product={item} />)}</div> : <EmptyState title="当前分类暂无其他商品" />}</section>
    </section>
  );
}
