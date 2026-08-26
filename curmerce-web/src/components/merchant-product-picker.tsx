"use client";

import { PackageSearch, Search } from "lucide-react";
import { KeyboardEvent, useEffect, useState } from "react";
import { EmptyState } from "@/components/empty-state";
import { MediaImage } from "@/components/media-image";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { adminProductApi } from "@/lib/api/admin-product";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { formatMoney } from "@/lib/format";
import type { ProductAdmin } from "@/lib/types/api";

const PAGE_SIZE = 8;

export function MerchantProductPicker({ enabled, selected, excludedSkuIds = [], onSelect }: {
  enabled: boolean;
  selected?: ProductAdmin;
  excludedSkuIds?: number[];
  onSelect: (product: ProductAdmin) => void;
}) {
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const excluded = new Set(excludedSkuIds);

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    setLoading(true);
    setError(null);
    void adminProductApi.pageOwn({ pageNo, pageSize: PAGE_SIZE, auditStatus: 2, saleStatus: 1, name: keyword })
      .then((page) => {
        if (!active) return;
        setProducts(page.list ?? []);
        setTotal(page.total ?? 0);
      })
      .catch((cause) => {
        if (!active) return;
        setProducts([]);
        setTotal(0);
        setError(cause instanceof CurmerceApiError ? cause.message : "可选商品加载失败");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [enabled, keyword, pageNo]);

  function search() {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  }

  function searchOnEnter(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== "Enter") return;
    event.preventDefault();
    search();
  }

  function availableSkuCount(product: ProductAdmin) {
    return product.skus.filter((sku) => sku.id && sku.status === 0 && sku.stock > 0 && !excluded.has(sku.id)).length;
  }

  const availableProducts = products.filter((product) => (availableSkuCount(product) > 0 || product.id === selected?.id) && product.id !== selected?.id);

  return (
    <div className="merchant-product-picker">
      <div aria-label="搜索可选商品" className="product-picker-search" role="search">
        <Search aria-hidden="true" size={16} />
        <input aria-label="商品名称" placeholder="按商品名称搜索" value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} onKeyDown={searchOnEnter} />
        <button type="button" onClick={search}>搜索</button>
      </div>
      {selected ? <div className="merchant-product-picker__selected"><span>已选择</span><ProductIdentity product={selected} skuCount={availableSkuCount(selected)} /></div> : null}
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <div className="order-list-skeleton"><span /><span /></div> : null}
      {!loading && !error && !availableProducts.length && !selected ? <EmptyState icon={<PackageSearch aria-hidden="true" size={21} />} title="没有可选商品" description={keyword ? "换一个商品名称继续搜索。" : "需要先准备已审核、已上架且有库存的商品。"} /> : null}
      {!loading && !error && !availableProducts.length && selected ? <p className="product-picker-selected-note">当前商品已选定，可继续搜索以更换商品。</p> : null}
      {!loading && availableProducts.length ? <div className="merchant-product-picker__results">{availableProducts.map((product) => {
        const skuCount = availableSkuCount(product);
        const chosen = product.id === selected?.id;
        return <button aria-pressed={chosen} disabled={!skuCount && !chosen} key={product.id} type="button" onClick={() => onSelect(product)}><ProductIdentity product={product} skuCount={skuCount} /><b>{chosen ? "已选择" : "选择"}</b></button>;
      })}</div> : null}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
    </div>
  );
}

function ProductIdentity({ product, skuCount }: { product: ProductAdmin; skuCount: number }) {
  const prices = product.skus.filter((sku) => sku.status === 0 && sku.stock > 0).map((sku) => sku.price);
  return <span className="merchant-product-picker__identity"><MediaImage alt="" fallback={<i aria-hidden="true">C</i>} src={assetUrl(product.mainImageUrl)} /><span><strong>{product.name}</strong><small>{product.code} · {skuCount} 个可用 SKU{prices.length ? ` · ${formatMoney(Math.min(...prices))} 起` : ""}</small></span></span>;
}
