import Link from "next/link";
import { assetUrl } from "@/lib/api/client";
import { formatMoney, formatStock } from "@/lib/format";
import type { PublicProductSummary } from "@/lib/types/api";

export function ProductCard({ product }: { product: PublicProductSummary }) {
  const image = assetUrl(product.mainImageUrl);
  return (
    <article className="product-card">
      <Link className="product-card__image" href={`/products/${product.id}`}>
        {image ? <img src={image} alt={product.name} /> : <span className="product-card__placeholder">CURMERCE</span>}
        {!product.available ? <span className="product-card__sold-out">暂时缺货</span> : null}
      </Link>
      <div className="product-card__body">
        <div className="product-card__store">{product.storeName || "Curmerce 店铺"}</div>
        <Link className="product-card__name" href={`/products/${product.id}`}>{product.name}</Link>
        {product.subtitle ? <p className="product-card__subtitle">{product.subtitle}</p> : null}
        <div className="product-card__footer">
          <strong>{formatMoney(product.minPrice)}</strong>
          {product.minMarketPrice && product.minMarketPrice > product.minPrice ? (
            <del>{formatMoney(product.minMarketPrice)}</del>
          ) : null}
          <span>{formatStock(product.totalStock)}</span>
        </div>
      </div>
    </article>
  );
}
