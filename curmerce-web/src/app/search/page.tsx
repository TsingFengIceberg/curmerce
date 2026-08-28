"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useEffect, useMemo, useState } from "react";
import { ArrowRight, MessageCircle, PackageSearch, Search as SearchIcon } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { MediaImage } from "@/components/media-image";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { ProductCard } from "@/components/product-card";
import { CurmerceApiError, assetUrl } from "@/lib/api/client";
import { searchApi } from "@/lib/api/search";
import { formatDateTime } from "@/lib/format";
import type { PublicProductSummary, SearchPostDocument, SearchProductDocument } from "@/lib/types/api";

const PAGE_SIZE = 12;
type SearchType = "products" | "posts";

function numberValue(value: number | string | null | undefined, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function productDocumentToSummary(document: SearchProductDocument): PublicProductSummary {
  const id = numberValue(document.productId ?? document.id);
  return {
    id,
    categoryId: numberValue(document.categoryId),
    storeId: document.storeId == null ? null : numberValue(document.storeId),
    storeName: document.storeName || "Curmerce 店铺",
    sellerType: document.sellerType ?? undefined,
    sellerUserId: document.sellerUserId == null ? null : numberValue(document.sellerUserId),
    sellerName: document.sellerName,
    name: document.name || "未命名商品",
    subtitle: document.subtitle,
    mainImageUrl: document.mainImageUrl,
    minPrice: numberValue(document.minPrice),
    minMarketPrice: document.minMarketPrice == null ? null : numberValue(document.minMarketPrice),
    totalStock: numberValue(document.totalStock),
    available: document.available ?? document.visible ?? true,
  };
}

function postId(document: SearchPostDocument) {
  return numberValue(document.postId ?? document.id);
}

function SearchContent() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const keyword = searchParams.get("keyword") ?? "";
  const type = searchParams.get("type") === "posts" ? "posts" : "products";
  const pageNo = Math.max(1, numberValue(searchParams.get("page"), 1));
  const [keywordInput, setKeywordInput] = useState(keyword);
  const [products, setProducts] = useState<SearchProductDocument[]>([]);
  const [posts, setPosts] = useState<SearchPostDocument[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => setKeywordInput(keyword), [keyword]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    const request = type === "products"
      ? searchApi.products({ keyword, pageNo, pageSize: PAGE_SIZE })
      : searchApi.posts({ keyword, pageNo, pageSize: PAGE_SIZE });
    void request.then((result) => {
      if (!active) return;
      if (type === "products") setProducts(result.results ?? []);
      else setPosts(result.results ?? []);
      setTotal(result.total ?? 0);
    }).catch((cause) => {
      if (!active) return;
      setProducts([]);
      setPosts([]);
      setTotal(0);
      setError(cause instanceof CurmerceApiError ? cause.message : "搜索服务暂时不可用");
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, pageNo, type]);

  function updateQuery(changes: Record<string, string | number | undefined>) {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === "") params.delete(key);
      else params.set(key, String(value));
    }
    router.push(`${pathname}${params.toString() ? `?${params.toString()}` : ""}`);
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    updateQuery({ keyword: keywordInput.trim(), page: undefined });
  }

  function changeType(nextType: SearchType) {
    updateQuery({ type: nextType === "products" ? undefined : nextType, page: undefined });
  }

  const productResults = useMemo(() => products.map(productDocumentToSummary), [products]);
  const hasResults = type === "products" ? productResults.length > 0 : posts.length > 0;

  return (
    <section className="content-section search-page">
      <div className="search-page__heading"><div><p className="eyebrow">CURMERCE SEARCH</p><h1>搜索</h1><p>在公开商品和社区分享中找到下一步。</p></div><Link className="button button--secondary" href={type === "products" ? "/catalog" : "/community"}><ArrowRight aria-hidden="true" size={17} />打开完整列表</Link></div>
      <form className="catalog-search search-page__form" onSubmit={submit}><SearchIcon aria-hidden="true" size={19} /><input aria-label="全站搜索" placeholder="搜索商品名称、描述或社区内容" value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} /><button className="button button--primary" type="submit">搜索</button></form>
      <div className="search-page__tabs" aria-label="搜索范围" role="tablist">
        <button aria-selected={type === "products"} className={type === "products" ? "search-page__tab search-page__tab--active" : "search-page__tab"} role="tab" type="button" onClick={() => changeType("products")}><PackageSearch aria-hidden="true" size={17} />商品</button>
        <button aria-selected={type === "posts"} className={type === "posts" ? "search-page__tab search-page__tab--active" : "search-page__tab"} role="tab" type="button" onClick={() => changeType("posts")}><MessageCircle aria-hidden="true" size={17} />社区内容</button>
      </div>
      {error ? <Notice>{error}。你仍可直接浏览商城或社区。</Notice> : null}
      <div className="search-page__summary"><strong>{keyword ? `“${keyword}”的${type === "products" ? "商品" : "社区内容"}结果` : `全部${type === "products" ? "商品" : "社区内容"}`}</strong><span>{loading ? "正在搜索…" : `共 ${total} 条`}</span></div>
      {loading ? <div className={type === "products" ? "catalog-skeleton search-page__skeleton" : "search-post-skeleton"}>{Array.from({ length: 6 }, (_, index) => <span key={index} />)}</div> : !hasResults ? <EmptyState title={keyword ? "没有找到匹配内容" : "暂时没有可搜索内容"} description="换一个关键词，或先浏览完整的商城和社区列表。" action={{ href: type === "products" ? "/catalog" : "/community", label: type === "products" ? "浏览商城" : "浏览社区" }} /> : type === "products" ? <div className="product-grid search-product-grid">{productResults.map((product) => <ProductCard key={product.id} product={product} />)}</div> : <div className="search-post-list">{posts.map((post) => <Link className="search-post-result" href={`/community/${postId(post)}`} key={postId(post)}><MediaImage alt="" fallback={<span className="search-post-result__placeholder"><MessageCircle aria-hidden="true" size={22} /></span>} src={assetUrl(post.mediaUrls?.[0])} /><div><span className="search-post-result__meta">{post.authorNickname || "Curmerce 用户"} · {formatDateTime(post.createTime)}</span><h2>{post.title || "未命名帖子"}</h2><p>{post.content || "暂无正文"}</p><small>{numberValue(post.likeCount)} 赞 · {numberValue(post.commentCount)} 评论{post.productIds?.length ? ` · ${post.productIds.length} 个关联商品` : ""}</small></div><ArrowRight aria-hidden="true" size={18} /></Link>)}</div>}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={(page) => updateQuery({ page: page === 1 ? undefined : page })} />
    </section>
  );
}

export default function SearchPage() {
  return <Suspense fallback={<section className="content-section"><div className="catalog-skeleton">{Array.from({ length: 6 }, (_, index) => <span key={index} />)}</div></section>}><SearchContent /></Suspense>;
}
