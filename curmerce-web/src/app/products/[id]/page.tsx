"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Notice } from "@/components/notice";
import { catalogApi } from "@/lib/api/catalog";
import { cartApi } from "@/lib/api/cart";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { formatMoney, formatStock } from "@/lib/format";
import type { PublicProductDetail, PublicProductSku } from "@/lib/types/api";

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [product, setProduct] = useState<PublicProductDetail | null>(null);
  const [selectedSkuId, setSelectedSkuId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
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
      const firstAvailable = response?.skus?.find((sku) => sku.available && sku.stock > 0) ?? response?.skus?.[0];
      setSelectedSkuId(firstAvailable?.id ?? null);
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
  const currentImage = assetUrl(selectedSku?.imageUrl || product?.imageUrls?.[0] || product?.mainImageUrl);
  const currentPrice = selectedSku?.price ?? product?.minPrice ?? 0;
  const maxQuantity = Math.max(1, Math.min(selectedSku?.stock ?? product?.totalStock ?? 1, 99));

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
          {product.imageUrls?.length > 1 ? (
            <div className="product-detail__thumbnails">
              {product.imageUrls.map((image) => {
                const source = assetUrl(image);
                return source ? <img key={image} src={source} alt="商品展示图" /> : null;
              })}
            </div>
          ) : null}
        </div>
        <div className="product-detail__info">
          <p className="eyebrow">{product.storeName || "CURMERCE STORE"}</p>
          <h1>{product.name}</h1>
          {product.subtitle ? <p className="product-detail__subtitle">{product.subtitle}</p> : null}
          <div className="product-detail__price">{formatMoney(currentPrice)}</div>
          {product.minMarketPrice && product.minMarketPrice > currentPrice ? <del>{formatMoney(product.minMarketPrice)}</del> : null}
          <p className="product-detail__stock">{formatStock(selectedSku?.stock ?? product.totalStock)}</p>
          {product.skus?.length > 0 ? (
            <div className="sku-selector">
              <div className="product-detail__label">选择规格</div>
              <div className="sku-options">
                {product.skus.map((sku) => (
                  <button
                    className={`sku-option${selectedSkuId === sku.id ? " sku-option--active" : ""}`}
                    disabled={!sku.available || sku.stock < 1}
                    key={sku.id}
                    type="button"
                    onClick={() => { setSelectedSkuId(sku.id); setQuantity(1); }}
                  >
                    <span>{sku.specificationValues?.map((item) => `${item.name}: ${item.value}`).join(" / ") || "默认规格"}</span>
                    <small>{formatMoney(sku.price)}</small>
                  </button>
                ))}
              </div>
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
          <button className="button button--primary product-detail__add" disabled={adding || !selectedSku?.available} type="button" onClick={() => void addToCart()}>
            {adding ? "加入中…" : "加入购物车"}
          </button>
          <p className="product-detail__hint">当前版本按店铺维度结算，一次订单只包含一个店铺的商品。</p>
        </div>
      </div>
      <article className="product-description">
        <p className="eyebrow">PRODUCT NOTE</p>
        <h2>商品说明</h2>
        <div>{product.description || "商家暂未补充商品说明。"}</div>
      </article>
    </section>
  );
}
