"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useMemo, useState } from "react";
import { Notice } from "@/components/notice";
import { adminProductApi, adminStoreApi } from "@/lib/api/admin-product";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { ProductAdmin, ProductSaveInput, ProductSkuInput, PublicCategoryNode, StoreSummary } from "@/lib/types/api";
import { catalogApi } from "@/lib/api/catalog";
import { ensureMerchantOwner } from "@/lib/auth/guards";

interface SkuForm {
  id?: number;
  code: string;
  specs: string;
  imageUrl: string;
  price: string;
  marketPrice: string;
  stock: string;
  status: string;
  sort: string;
}

interface ProductForm {
  id?: number;
  code: string;
  categoryId: string;
  name: string;
  subtitle: string;
  mainImageUrl: string;
  imageUrls: string;
  description: string;
  sort: string;
  skus: SkuForm[];
}

const emptySku: SkuForm = { code: "", specs: "", imageUrl: "", price: "0", marketPrice: "0", stock: "0", status: "1", sort: "0" };
const emptyForm: ProductForm = { code: "", categoryId: "", name: "", subtitle: "", mainImageUrl: "", imageUrls: "", description: "", sort: "0", skus: [{ ...emptySku }] };

const auditLabels: Record<number, string> = { 0: "草稿", 1: "待审核", 2: "审核通过", 3: "已驳回" };
const saleLabels: Record<number, string> = { 0: "下架", 1: "上架" };

function flattenPublic(nodes: PublicCategoryNode[], depth = 0): Array<{ node: PublicCategoryNode; depth: number }> {
  return nodes.flatMap((node) => [{ node, depth }, ...flattenPublic(node.children ?? [], depth + 1)]);
}

function parseSpecs(value: string) {
  return value.split(";").map((item) => item.trim()).filter(Boolean).map((item) => {
    const [name, ...rest] = item.split("=");
    return { name: name.trim(), value: rest.join("=").trim() };
  }).filter((item) => item.name && item.value);
}

function formatSpecs(values?: Array<{ name: string; value: string }> | null) {
  return values?.map((item) => `${item.name}=${item.value}`).join(";") ?? "";
}

function productToForm(product: ProductAdmin): ProductForm {
  return {
    id: product.id,
    code: product.code,
    categoryId: String(product.categoryId),
    name: product.name,
    subtitle: product.subtitle ?? "",
    mainImageUrl: product.mainImageUrl ?? "",
    imageUrls: product.imageUrls?.join("\n") ?? "",
    description: product.description ?? "",
    sort: String(product.sort ?? 0),
    skus: product.skus.length ? product.skus.map((sku) => ({ id: sku.id, code: sku.code, specs: formatSpecs(sku.specificationValues), imageUrl: sku.imageUrl ?? "", price: String(sku.price), marketPrice: String(sku.marketPrice ?? 0), stock: String(sku.stock), status: String(sku.status), sort: String(sku.sort ?? 0) })) : [{ ...emptySku }],
  };
}

export default function MerchantProductsPage() {
  const router = useRouter();
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [store, setStore] = useState<StoreSummary | null>(null);
  const [categories, setCategories] = useState<Array<{ node: PublicCategoryNode; depth: number }>>([]);
  const [form, setForm] = useState<ProductForm>(emptyForm);
  const [editing, setEditing] = useState<ProductAdmin | null>(null);
  const [auditStatus, setAuditStatus] = useState("");
  const [saleStatus, setSaleStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void ensureMerchantOwner(router).then((allowed) => {
      if (allowed) void Promise.all([loadProducts(), loadStore(), loadCategories()]);
    });
  }, [router, auditStatus, saleStatus]);

  async function loadProducts() {
    setLoading(true);
    try {
      const response = await adminProductApi.pageOwn({ pageNo: 1, pageSize: 20, auditStatus: auditStatus ? Number(auditStatus) : undefined, saleStatus: saleStatus ? Number(saleStatus) : undefined });
      setProducts(response?.list ?? []);
    } catch (cause) {
      handleError(cause, "商品列表加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function loadStore() {
    try {
      setStore(await adminStoreApi.own());
    } catch (cause) {
      handleError(cause, "店铺信息加载失败");
    }
  }

  async function loadCategories() {
    try {
      const response = await catalogApi.categoryTree();
      setCategories(flattenPublic(response ?? []));
    } catch (cause) {
      handleError(cause, "分类加载失败");
    }
  }

  function handleError(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      router.replace("/merchant/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  function startCreate() {
    setEditing(null);
    setForm(emptyForm);
    setError(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function startEdit(product: ProductAdmin) {
    setBusy(true);
    setError(null);
    try {
      const detail = await adminProductApi.detailOwn(product.id);
      setEditing(detail);
      setForm(productToForm(detail));
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (cause) {
      handleError(cause, "商品详情加载失败");
    } finally {
      setBusy(false);
    }
  }

  function updateProduct<K extends keyof ProductForm>(key: K, value: ProductForm[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function updateSku(index: number, key: keyof SkuForm, value: string) {
    setForm((current) => ({ ...current, skus: current.skus.map((sku, skuIndex) => skuIndex === index ? { ...sku, [key]: value } : sku) }));
  }

  function toPayload(): ProductSaveInput | null {
    if (!store || !form.categoryId) return null;
    return {
      storeId: store.id,
      categoryId: Number(form.categoryId),
      name: form.name.trim(),
      subtitle: form.subtitle.trim(),
      mainImageUrl: form.mainImageUrl.trim(),
      imageUrls: form.imageUrls.split(/[\n,]/).map((item) => item.trim()).filter(Boolean),
      description: form.description.trim(),
      sort: Number(form.sort),
      skus: form.skus.map((sku): ProductSkuInput => ({ id: sku.id, code: sku.code.trim(), specificationValues: parseSpecs(sku.specs), imageUrl: sku.imageUrl.trim(), price: Number(sku.price), marketPrice: Number(sku.marketPrice), stock: Number(sku.stock), status: Number(sku.status), sort: Number(sku.sort) })),
    };
  }

  async function saveProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.name.trim() || !form.categoryId || !form.mainImageUrl.trim() || !form.description.trim() || !store) {
      setError("请填写商品名称、分类、主图、描述，并确保店铺已加载");
      return;
    }
    if (!editing && form.code.trim().length < 2) {
      setError("新商品编码至少需要 2 个字符");
      return;
    }
    if (form.skus.some((sku) => !sku.code.trim() || Number(sku.price) < 0 || Number(sku.stock) < 0)) {
      setError("每个 SKU 都需要填写编码，价格和库存不能为负数");
      return;
    }
    const payload = toPayload();
    if (!payload) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (editing) await adminProductApi.updateOwn({ ...payload, id: editing.id });
      else await adminProductApi.createOwn({ ...payload, code: form.code.trim() });
      setMessage(editing ? "商品草稿已保存" : "商品草稿已创建");
      setEditing(null);
      setForm(emptyForm);
      await loadProducts();
    } catch (cause) {
      handleError(cause, "保存商品失败");
    } finally {
      setBusy(false);
    }
  }

  async function transition(product: ProductAdmin, action: "submit" | "list" | "delist") {
    setBusyId(product.id);
    setError(null);
    setMessage(null);
    try {
      if (action === "submit") await adminProductApi.submitOwn(product.id);
      if (action === "list") await adminProductApi.listOwn(product.id);
      if (action === "delist") await adminProductApi.delistOwn(product.id);
      setMessage(action === "submit" ? "商品已提交审核" : action === "list" ? "商品已上架" : "商品已下架");
      await loadProducts();
    } catch (cause) {
      handleError(cause, "商品状态更新失败");
    } finally {
      setBusyId(null);
    }
  }

  async function logout() {
    clearAdminToken();
    router.replace("/merchant/login");
  }

  const categoryOptions = useMemo(() => categories, [categories]);

  return (
    <section className="content-section admin-page merchant-products-page">
      <div className="section-heading">
        <div><p className="eyebrow">MERCHANT · CATALOG</p><h1>我的商品</h1><p>{store ? `${store.name} · ${products.length} 条当前记录` : "正在读取店铺信息…"}</p></div>
        <div className="inline-actions"><Link className="button button--secondary" href="/merchant/refunds">退款审核</Link><button className="button button--secondary" type="button" onClick={() => void logout()}>退出后台</button></div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <form className="orders-panel product-editor" onSubmit={saveProduct}>
        <div className="panel-heading"><h2>{editing ? "编辑商品草稿" : "创建商品草稿"}</h2>{editing ? <button className="text-button" type="button" onClick={startCreate}>取消编辑</button> : null}</div>
        <div className="admin-form-grid">
          {!editing ? <label className="field"><span>商品编码</span><input maxLength={64} onChange={(event) => updateProduct("code", event.target.value)} placeholder="例如 mug-001" value={form.code} /></label> : <div className="form-readonly">商品编码：{editing.code}</div>}
          <label className="field"><span>商品名称</span><input maxLength={128} onChange={(event) => updateProduct("name", event.target.value)} value={form.name} /></label>
          <label className="field"><span>分类</span><select onChange={(event) => updateProduct("categoryId", event.target.value)} value={form.categoryId}><option value="">请选择分类</option>{categoryOptions.map(({ node, depth }) => <option key={node.id} value={node.id}>{"　".repeat(depth)}{node.name}</option>)}</select></label>
          <label className="field"><span>副标题</span><input maxLength={255} onChange={(event) => updateProduct("subtitle", event.target.value)} value={form.subtitle} /></label>
          <label className="field"><span>主图地址</span><input maxLength={1024} onChange={(event) => updateProduct("mainImageUrl", event.target.value)} value={form.mainImageUrl} /></label>
          <label className="field"><span>排序</span><input min="0" onChange={(event) => updateProduct("sort", event.target.value)} type="number" value={form.sort} /></label>
        </div>
        <label className="field"><span>更多图片地址（每行一个）</span><textarea onChange={(event) => updateProduct("imageUrls", event.target.value)} rows={3} value={form.imageUrls} /></label>
        <label className="field"><span>商品描述</span><textarea maxLength={100000} onChange={(event) => updateProduct("description", event.target.value)} rows={5} value={form.description} /></label>
        <div className="sku-editor-heading"><h3>SKU 与库存</h3><button className="button button--secondary button--small" type="button" onClick={() => updateProduct("skus", [...form.skus, { ...emptySku }])}>增加 SKU</button></div>
        <div className="sku-editor-list">
          {form.skus.map((sku, index) => (
            <div className="sku-editor-row" key={sku.id ?? index}>
              <div className="sku-editor-row__top"><strong>SKU {index + 1}</strong>{form.skus.length > 1 ? <button className="text-button text-button--danger" type="button" onClick={() => updateProduct("skus", form.skus.filter((_, skuIndex) => skuIndex !== index))}>删除</button> : null}</div>
              <div className="admin-form-grid admin-form-grid--sku">
                <label className="field"><span>SKU 编码</span><input maxLength={64} onChange={(event) => updateSku(index, "code", event.target.value)} value={sku.code} /></label>
                <label className="field"><span>规格（颜色=红;尺寸=M）</span><input onChange={(event) => updateSku(index, "specs", event.target.value)} value={sku.specs} /></label>
                <label className="field"><span>价格（分）</span><input min="0" onChange={(event) => updateSku(index, "price", event.target.value)} type="number" value={sku.price} /></label>
                <label className="field"><span>市场价（分）</span><input min="0" onChange={(event) => updateSku(index, "marketPrice", event.target.value)} type="number" value={sku.marketPrice} /></label>
                <label className="field"><span>库存</span><input min="0" onChange={(event) => updateSku(index, "stock", event.target.value)} type="number" value={sku.stock} /></label>
                <label className="field"><span>状态</span><select onChange={(event) => updateSku(index, "status", event.target.value)} value={sku.status}><option value="1">启用</option><option value="0">停用</option></select></label>
              </div>
            </div>
          ))}
        </div>
        <button className="button button--primary" disabled={busy} type="submit">{busy ? "保存中…" : editing ? "保存草稿" : "创建草稿"}</button>
      </form>
      <div className="orders-panel">
        <div className="panel-heading"><h2>商品列表</h2><div className="inline-actions"><select aria-label="审核状态" onChange={(event) => setAuditStatus(event.target.value)} value={auditStatus}><option value="">全部审核状态</option><option value="0">草稿</option><option value="1">待审核</option><option value="2">审核通过</option><option value="3">已驳回</option></select><select aria-label="上架状态" onChange={(event) => setSaleStatus(event.target.value)} value={saleStatus}><option value="">全部销售状态</option><option value="0">下架</option><option value="1">上架</option></select><button className="button button--secondary button--small" type="button" onClick={startCreate}>新建商品</button></div></div>
        {loading ? <p className="empty-state">商品加载中…</p> : null}
        {!loading && products.length === 0 ? <p className="empty-state">当前没有商品记录。</p> : null}
        <div className="admin-record-list">
          {products.map((product) => (
            <article className="product-admin-card" key={product.id}>
              <div className="product-admin-card__image">{product.mainImageUrl ? <img alt={product.name} src={product.mainImageUrl} /> : <span>C</span>}</div>
              <div className="product-admin-card__body"><div className="admin-record-card__top"><div><strong>{product.name}</strong><small>{product.code} · {formatDateTime(product.updateTime ?? product.createTime)}</small></div><div className="inline-actions"><span className="tag product-status">{auditLabels[product.auditStatus] ?? `审核 ${product.auditStatus}`}</span><span className="tag product-status">{saleLabels[product.saleStatus] ?? `销售 ${product.saleStatus}`}</span></div></div><p>{product.subtitle || product.description || "暂无商品简介"}</p><span className="product-admin-card__meta">{product.skus.length} 个 SKU · 起售价 {formatMoney(Math.min(...product.skus.map((sku) => sku.price)))}</span><div className="inline-actions"><button className="text-button" type="button" onClick={() => void startEdit(product)}>编辑</button>{product.auditStatus === 0 || product.auditStatus === 3 ? <button className="text-button" disabled={busyId === product.id} type="button" onClick={() => void transition(product, "submit")}>提交审核</button> : null}{product.auditStatus === 2 && product.saleStatus === 0 ? <button className="text-button" disabled={busyId === product.id} type="button" onClick={() => void transition(product, "list")}>上架</button> : null}{product.saleStatus === 1 ? <button className="text-button" disabled={busyId === product.id} type="button" onClick={() => void transition(product, "delist")}>下架</button> : null}</div></div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
