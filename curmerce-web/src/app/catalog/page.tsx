"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { CategoryTree } from "@/components/category-tree";
import { Notice } from "@/components/notice";
import { ProductCard } from "@/components/product-card";
import { catalogApi } from "@/lib/api/catalog";
import { CurmerceApiError } from "@/lib/api/client";
import type { PublicCategoryNode, PublicProductSummary } from "@/lib/types/api";

const PAGE_SIZE = 12;

export default function CatalogPage() {
  const [categories, setCategories] = useState<PublicCategoryNode[]>([]);
  const [products, setProducts] = useState<PublicProductSummary[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [categoryLoading, setCategoryLoading] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadCategories();
  }, []);

  useEffect(() => {
    void loadProducts();
  }, [pageNo, selectedCategoryId, keyword]);

  async function loadCategories() {
    setCategoryLoading(true);
    try {
      const response = await catalogApi.categoryTree();
      setCategories(response ?? []);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品分类加载失败");
    } finally {
      setCategoryLoading(false);
    }
  }

  async function loadProducts() {
    setLoading(true);
    setError(null);
    try {
      const response = await catalogApi.productPage({
        pageNo,
        pageSize: PAGE_SIZE,
        categoryId: selectedCategoryId ?? undefined,
        keyword,
      });
      setProducts(response?.list ?? []);
      setTotal(response?.total ?? 0);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品加载失败");
      setProducts([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNo(1);
    setKeyword(keywordInput.trim());
  }

  function selectCategory(categoryId: number | null) {
    setPageNo(1);
    setSelectedCategoryId(categoryId);
  }

  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <section className="content-section catalog-page">
      <div className="section-heading catalog-heading">
        <div>
          <p className="eyebrow">DISCOVER · COMMERCE</p>
          <h1>把兴趣，变成下一件喜欢的东西。</h1>
          <p>从 Curmerce 商家目录开始探索，先看商品，再进入可信交易。</p>
        </div>
        <Link className="button button--secondary" href="/cart">查看购物车 →</Link>
      </div>
      {error ? <Notice>{error}</Notice> : null}
      <div className="catalog-layout">
        <aside className="catalog-sidebar">
          <div className="panel-heading">
            <h2>商品分类</h2>
            <span>{categoryLoading ? "加载中…" : `${categories.length} 个一级分类`}</span>
          </div>
          {categoryLoading ? <p className="empty-state">正在读取分类…</p> : <CategoryTree categories={categories} selectedId={selectedCategoryId} onSelect={selectCategory} />}
        </aside>
        <div className="catalog-results">
          <form className="catalog-search" onSubmit={submitSearch}>
            <input value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} placeholder="搜索商品名称或描述" />
            <button className="button button--primary" type="submit">搜索</button>
          </form>
          <div className="catalog-results__meta">
            <span>{keyword ? `“${keyword}”的结果` : selectedCategoryId ? "当前分类商品" : "全部商品"}</span>
            <span>{total} 件商品</span>
          </div>
          {loading ? <p className="empty-state catalog-loading">商品加载中…</p> : null}
          {!loading && products.length === 0 ? <p className="empty-state catalog-loading">暂时没有符合条件的商品。</p> : null}
          {!loading && products.length > 0 ? (
            <div className="product-grid">
              {products.map((product) => <ProductCard key={product.id} product={product} />)}
            </div>
          ) : null}
          {total > 0 ? (
            <div className="pagination">
              <button className="button button--secondary" disabled={pageNo <= 1} type="button" onClick={() => setPageNo((current) => current - 1)}>上一页</button>
              <span>第 {pageNo} / {pageCount} 页</span>
              <button className="button button--secondary" disabled={pageNo >= pageCount} type="button" onClick={() => setPageNo((current) => current + 1)}>下一页</button>
            </div>
          ) : null}
        </div>
      </div>
    </section>
  );
}
