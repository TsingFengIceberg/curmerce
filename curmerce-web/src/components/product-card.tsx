import Link from "next/link";
import { MediaImage } from "@/components/media-image";
import { assetUrl } from "@/lib/api/client";
import { formatMoney, formatStock } from "@/lib/format";
import type { PublicProductSummary } from "@/lib/types/api";

export function ProductCard({ product }: { product: PublicProductSummary }) {
  const image = assetUrl(product.mainImageUrl);
  return (
    <Link aria-label={`查看商品 ${product.name}`} className="product-card" href={`/products/${product.id}`}>
      <span className="product-card__image">
        <MediaImage alt={product.name} fallbackClassName="product-card__placeholder" fallbackLabel={`${product.name}暂无图片`} src={image} />
        {!product.available ? <span className="product-card__sold-out">暂时缺货</span> : null}
      </span>
      <div className="product-card__body">
        <div className="product-card__store">{product.storeName || "Curmerce 店铺"}</div>
        <span className="product-card__name">{product.name}</span>
        {product.subtitle ? <p className="product-card__subtitle">{product.subtitle}</p> : null}
        <div className="product-card__footer">
          <strong>{formatMoney(product.minPrice)}</strong>
          {product.minMarketPrice && product.minMarketPrice > product.minPrice ? (
            <del>{formatMoney(product.minMarketPrice)}</del>
          ) : null}
          <span>{formatStock(product.totalStock)}</span>
        </div>
      </div>
    </Link>
  );
}
