"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useEffect, useRef, useState } from "react";
import { Filter, Search, ShoppingCart, SlidersHorizontal, X } from "lucide-react";
import { CategoryTree } from "@/components/category-tree";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { ProductCard } from "@/components/product-card";
import { catalogApi } from "@/lib/api/catalog";
import { CurmerceApiError } from "@/lib/api/client";
import type { PublicCategoryNode, PublicProductSummary } from "@/lib/types/api";

const PAGE_SIZE = 12;
type SortValue = "" | "latest" | "priceAsc" | "priceDesc";

function numberParam(value: string | null) {
  const parsed = Number(value);
  return value && Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
}

function findCategoryName(nodes: PublicCategoryNode[], id?: number): string | null {
  if (!id) return null;
  for (const node of nodes) {
    if (node.id === id) return node.name;
    const nested = findCategoryName(node.children ?? [], id);
    if (nested) return nested;
  }
  return null;
}

function CatalogContent() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [categories, setCategories] = useState<PublicCategoryNode[]>([]);
  const [products, setProducts] = useState<PublicProductSummary[]>([]);
  const [keywordInput, setKeywordInput] = useState(searchParams.get("keyword") ?? "");
  const [minPriceInput, setMinPriceInput] = useState(searchParams.get("minPrice") ?? "");
  const [maxPriceInput, setMaxPriceInput] = useState(searchParams.get("maxPrice") ?? "");
  const [storeInput, setStoreInput] = useState(searchParams.get("store") ?? "");
  const [filterOpen, setFilterOpen] = useState(false);
  const [categoryLoading, setCategoryLoading] = useState(true);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const resultsRef = useRef<HTMLDivElement>(null);

  const pageNo = Math.max(1, numberParam(searchParams.get("page")) ?? 1);
  const categoryId = numberParam(searchParams.get("category"));
  const keyword = searchParams.get("keyword") ?? "";
  const minPrice = numberParam(searchParams.get("minPrice"));
  const maxPrice = numberParam(searchParams.get("maxPrice"));
  const storeKeyword = searchParams.get("store") ?? "";
  const sellerType = numberParam(searchParams.get("sellerType"));
  const inStock = searchParams.get("inStock") === "1";
  const sort = (searchParams.get("sort") ?? "") as SortValue;
  const activeFilterCount = [categoryId, keyword, minPrice !== undefined, maxPrice !== undefined, storeKeyword, sellerType, inStock, sort].filter(Boolean).length;

  useEffect(() => {
    setKeywordInput(keyword);
    setMinPriceInput(searchParams.get("minPrice") ?? "");
    setMaxPriceInput(searchParams.get("maxPrice") ?? "");
    setStoreInput(storeKeyword);
  }, [keyword, minPrice, maxPrice, storeKeyword, searchParams]);

  useEffect(() => {
    void catalogApi.categoryTree().then((response) => setCategories(response ?? [])).catch((cause) => setError(cause instanceof CurmerceApiError ? cause.message : "商品分类加载失败")).finally(() => setCategoryLoading(false));
  }, []);

  useEffect(() => {
    setLoading(true);
    setError(null);
    void catalogApi.productPage({
      pageNo,
      pageSize: PAGE_SIZE,
      categoryId,
      keyword,
      minPrice: minPrice === undefined ? undefined : Math.round(minPrice * 100),
      maxPrice: maxPrice === undefined ? undefined : Math.round(maxPrice * 100),
      inStock,
      sellerType,
      storeKeyword,
      sort: sort || undefined,
    }).then((response) => { setProducts(response?.list ?? []); setTotal(response?.total ?? 0); })
      .catch((cause) => { setError(cause instanceof CurmerceApiError ? cause.message : "商品加载失败"); setProducts([]); setTotal(0); })
      .finally(() => setLoading(false));
  }, [pageNo, categoryId, keyword, minPrice, maxPrice, inStock, sellerType, storeKeyword, sort]);

  function updateQuery(changes: Record<string, string | number | boolean | undefined>, resetPage = true) {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === "" || value === false) params.delete(key);
      else params.set(key, value === true ? "1" : String(value));
    }
    if (resetPage) params.delete("page");
    const query = params.toString();
    router.push(query ? `${pathname}?${query}` : pathname);
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    updateQuery({ keyword: keywordInput.trim() });
  }

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextMin = numberParam(minPriceInput);
    const nextMax = numberParam(maxPriceInput);
    if (nextMin !== undefined && nextMax !== undefined && nextMin > nextMax) {
      setError("最低价格不能高于最高价格");
      return;
    }
    updateQuery({ minPrice: nextMin, maxPrice: nextMax, store: storeInput.trim() });
    setFilterOpen(false);
  }

  function clearFilters() {
    setKeywordInput(""); setMinPriceInput(""); setMaxPriceInput(""); setStoreInput("");
    router.push(pathname);
  }

  function changePage(page: number) {
    updateQuery({ page }, false);
    window.setTimeout(() => resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 0);
  }

  const filterContents = (
    <>
      <div className="catalog-filter-panel__heading"><div><SlidersHorizontal aria-hidden="true" size={18} /><h2>筛选</h2></div>{activeFilterCount ? <button className="text-button" type="button" onClick={clearFilters}><X aria-hidden="true" size={14} />清空 {activeFilterCount} 项</button> : null}</div>
      <div className="catalog-filter-section"><h3>商品分类</h3>{categoryLoading ? <p className="filter-loading">分类加载中…</p> : <CategoryTree categories={categories} selectedId={categoryId ?? null} onSelect={(id) => updateQuery({ category: id ?? undefined })} />}</div>
      <form onSubmit={applyFilters}>
        <div className="catalog-filter-section"><h3>价格区间</h3><div className="price-range"><label><span>最低价</span><input min="0" step="0.01" inputMode="decimal" placeholder="¥ 0" value={minPriceInput} onChange={(event) => setMinPriceInput(event.target.value)} /></label><span>至</span><label><span>最高价</span><input min="0" step="0.01" inputMode="decimal" placeholder="不限" value={maxPriceInput} onChange={(event) => setMaxPriceInput(event.target.value)} /></label></div></div>
        <div className="catalog-filter-section"><h3>销售方式</h3><label className="filter-radio"><input checked={!sellerType} name="sellerType" type="radio" onChange={() => updateQuery({ sellerType: undefined })} />全部</label><label className="filter-radio"><input checked={sellerType === 1} name="sellerType" type="radio" onChange={() => updateQuery({ sellerType: 1 })} />商家商品</label><label className="filter-radio"><input checked={sellerType === 2} name="sellerType" type="radio" onChange={() => updateQuery({ sellerType: 2 })} />个人闲置</label></div>
        <div className="catalog-filter-section"><h3>店铺</h3><input className="filter-text-input" placeholder="搜索店铺名称" value={storeInput} onChange={(event) => setStoreInput(event.target.value)} /></div>
        <label className="filter-checkbox"><input checked={inStock} type="checkbox" onChange={(event) => updateQuery({ inStock: event.target.checked })} />仅看有货商品</label>
        <button className="button button--secondary button--full" type="submit"><Filter aria-hidden="true" size={17} />应用筛选</button>
      </form>
    </>
  );

  const activeFilters = [
    categoryId ? { key: "category", label: `分类：${findCategoryName(categories, categoryId) ?? categoryId}`, clear: () => updateQuery({ category: undefined }) } : null,
    keyword ? { key: "keyword", label: `关键词：${keyword}`, clear: () => updateQuery({ keyword: undefined }) } : null,
    minPrice !== undefined ? { key: "minPrice", label: `最低 ¥${minPrice}`, clear: () => updateQuery({ minPrice: undefined }) } : null,
    maxPrice !== undefined ? { key: "maxPrice", label: `最高 ¥${maxPrice}`, clear: () => updateQuery({ maxPrice: undefined }) } : null,
    storeKeyword ? { key: "store", label: `店铺：${storeKeyword}`, clear: () => updateQuery({ store: undefined }) } : null,
    sellerType ? { key: "sellerType", label: sellerType === 1 ? "商家商品" : "个人闲置", clear: () => updateQuery({ sellerType: undefined }) } : null,
    inStock ? { key: "inStock", label: "仅看有货", clear: () => updateQuery({ inStock: undefined }) } : null,
    sort ? { key: "sort", label: ({ latest: "最新上架", priceAsc: "价格升序", priceDesc: "价格降序" } as Record<string, string>)[sort], clear: () => updateQuery({ sort: undefined }) } : null,
  ].filter((item): item is NonNullable<typeof item> => Boolean(item));

  return (
    <section className="content-section catalog-page">
      <div className="catalog-page-heading">
        <div><p className="eyebrow">DISCOVER COMMERCE</p><h1>商城</h1><p>按兴趣、价格和销售方式找到适合自己的商品。</p></div>
        <Link className="button button--secondary" href="/cart"><ShoppingCart aria-hidden="true" size={18} />购物车</Link>
      </div>
      <form className="catalog-search catalog-search--product" onSubmit={submitSearch}><Search aria-hidden="true" size={19} /><input aria-label="搜索商品" value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} placeholder="搜索商品名称或描述" /><button className="button button--primary" type="submit">搜索</button></form>
      {error ? <Notice>{error}</Notice> : null}
      <div className="catalog-mobile-controls"><button className={activeFilterCount ? "button button--secondary filter-toggle--active" : "button button--secondary"} type="button" onClick={() => setFilterOpen(true)}><SlidersHorizontal aria-hidden="true" size={17} />筛选{activeFilterCount ? `（${activeFilterCount}）` : ""}</button></div>
      {activeFilters.length ? <div className="catalog-active-filters" aria-label="当前筛选条件">{activeFilters.map((item) => <button key={item.key} type="button" onClick={item.clear}>{item.label}<X aria-hidden="true" size={13} /></button>)}</div> : null}
      <div className="catalog-layout catalog-layout--product">
        <aside className="catalog-filter-panel catalog-filter-desktop">{filterContents}</aside>
        <div className="catalog-results catalog-results--product" ref={resultsRef}>
          <div className="catalog-results-toolbar"><div><strong>{keyword ? `“${keyword}”的结果` : "全部商品"}</strong><span>{loading ? "正在更新…" : `共 ${total} 件`}</span></div><label><span>排序</span><select aria-label="商品排序" value={sort} onChange={(event) => updateQuery({ sort: event.target.value })}><option value="">综合排序</option><option value="latest">最新上架</option><option value="priceAsc">价格从低到高</option><option value="priceDesc">价格从高到低</option></select></label></div>
          {loading ? <div className="catalog-skeleton">{Array.from({ length: 6 }, (_, index) => <span key={index} />)}</div> : products.length === 0 ? <EmptyState title="没有找到符合条件的商品" description="调整关键词或筛选条件后再试试。" action={{ href: "/catalog", label: "清空筛选" }} /> : <div className="product-grid">{products.map((product) => <ProductCard key={product.id} product={product} />)}</div>}
          <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={changePage} />
        </div>
      </div>
      <Drawer open={filterOpen} title="筛选商品" description="调整条件后查看匹配的商品。" onClose={() => setFilterOpen(false)}><div className="catalog-filter-panel catalog-filter-panel--drawer">{filterContents}</div></Drawer>
    </section>
  );
}

export default function CatalogPage() {
  return <Suspense fallback={<section className="content-section"><div className="catalog-skeleton">{Array.from({ length: 6 }, (_, index) => <span key={index} />)}</div></section>}><CatalogContent /></Suspense>;
}
