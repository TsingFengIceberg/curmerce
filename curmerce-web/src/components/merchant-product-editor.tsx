"use client";

import { ArrowLeft, Plus, Save, Trash2, X } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { ImageUploader } from "@/components/image-uploader";
import { Notice } from "@/components/notice";
import { adminProductApi, adminStoreApi } from "@/lib/api/admin-product";
import { catalogApi } from "@/lib/api/catalog";
import { CurmerceApiError } from "@/lib/api/client";
import { ensureMerchantOwner } from "@/lib/auth/guards";
import type { ProductAdmin, ProductSaveInput, ProductSkuInput, ProductSpecificationValueAdmin, PublicCategoryNode, StoreSummary } from "@/lib/types/api";

type SpecGroup = { id: string; name: string; values: string[] };
type SkuRow = { id?: number; specs: ProductSpecificationValueAdmin[]; code: string; imageUrl: string; priceYuan: string; marketPriceYuan: string; stock: string; status: string; sort: string };
type ProductForm = { code: string; categoryId: string; name: string; subtitle: string; images: string[]; description: string; sort: string };
type MerchantProductDraft = { form: ProductForm; groups: SpecGroup[]; rows: SkuRow[]; savedAt?: number };

const EMPTY_FORM: ProductForm = { code: "", categoryId: "", name: "", subtitle: "", images: [], description: "", sort: "0" };
const EMPTY_SKU: SkuRow = { specs: [], code: "", imageUrl: "", priceYuan: "", marketPriceYuan: "", stock: "0", status: "0", sort: "0" };

function flatten(nodes: PublicCategoryNode[], depth = 0): Array<{ node: PublicCategoryNode; depth: number }> {
  return nodes.flatMap((node) => [{ node, depth }, ...flatten(node.children ?? [], depth + 1)]);
}

function specKey(specs: ProductSpecificationValueAdmin[]) {
  return specs.map((item) => `${item.name}=${item.value}`).join("|");
}

function combinations(groups: SpecGroup[]): ProductSpecificationValueAdmin[][] {
  if (!groups.length) return [[]];
  if (groups.some((group) => !group.name.trim() || !group.values.length)) return [];
  return groups.reduce<ProductSpecificationValueAdmin[][]>((rows, group) => rows.flatMap((row) => group.values.map((value) => [...row, { name: group.name.trim(), value }])), [[]]);
}

function groupsFromProduct(product: ProductAdmin): SpecGroup[] {
  const valuesByName = new Map<string, string[]>();
  for (const sku of product.skus) {
    for (const spec of sku.specificationValues ?? []) {
      const values = valuesByName.get(spec.name) ?? [];
      if (!values.includes(spec.value)) values.push(spec.value);
      valuesByName.set(spec.name, values);
    }
  }
  return Array.from(valuesByName, ([name, values], index) => ({ id: `existing-${index}`, name, values }));
}

function rowsFromProduct(product: ProductAdmin): SkuRow[] {
  return product.skus.map((sku) => ({ id: sku.id, specs: sku.specificationValues ?? [], code: sku.code, imageUrl: sku.imageUrl ?? "", priceYuan: (sku.price / 100).toFixed(2), marketPriceYuan: ((sku.marketPrice ?? 0) / 100).toFixed(2), stock: String(sku.stock), status: String(sku.status), sort: String(sku.sort ?? 0) }));
}

export function MerchantProductEditor({ productId }: { productId?: number }) {
  const router = useRouter();
  const storageKey = `curmerce-merchant-product:${productId || "new"}`;
  const [store, setStore] = useState<StoreSummary | null>(null);
  const [categories, setCategories] = useState<Array<{ node: PublicCategoryNode; depth: number }>>([]);
  const [form, setForm] = useState<ProductForm>(EMPTY_FORM);
  const [groups, setGroups] = useState<SpecGroup[]>([]);
  const [rows, setRows] = useState<SkuRow[]>([{ ...EMPTY_SKU }]);
  const [valueDrafts, setValueDrafts] = useState<Record<string, string>>({});
  const [bulk, setBulk] = useState({ priceYuan: "", marketPriceYuan: "", stock: "" });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [autoSaved, setAutoSaved] = useState(false);
  const [recoveryDraft, setRecoveryDraft] = useState<MerchantProductDraft | null>(null);
  const [pendingHref, setPendingHref] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void ensureMerchantOwner(router).then((allowed) => { if (allowed) void load(); });
  }, [productId, router]);

  useEffect(() => {
    if (!dirty || loading) return;
    setAutoSaved(false);
    const timer = window.setTimeout(() => {
      window.localStorage.setItem(storageKey, JSON.stringify({ form, groups, rows, savedAt: Date.now() } satisfies MerchantProductDraft));
      setAutoSaved(true);
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [dirty, loading, form, groups, rows, storageKey]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
    };
    const guardLinks = (event: globalThis.MouseEvent) => {
      if (!dirty || event.defaultPrevented || event.button !== 0) return;
      const target = event.target as Element | null;
      const link = target?.closest("a[href]") as HTMLAnchorElement | null;
      if (!link || link.target === "_blank" || link.href === window.location.href) return;
      const destination = new URL(link.href, window.location.href);
      if (destination.origin !== window.location.origin) return;
      event.preventDefault();
      setPendingHref(`${destination.pathname}${destination.search}${destination.hash}`);
    };
    window.addEventListener("beforeunload", warn);
    document.addEventListener("click", guardLinks, true);
    return () => {
      window.removeEventListener("beforeunload", warn);
      document.removeEventListener("click", guardLinks, true);
    };
  }, [dirty]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [ownStore, tree, detail] = await Promise.all([adminStoreApi.own(), catalogApi.categoryTree(), productId ? adminProductApi.detailOwn(productId) : Promise.resolve(null)]);
      setStore(ownStore);
      setCategories(flatten(tree ?? []));
      if (detail) {
        setForm({ code: detail.code, categoryId: String(detail.categoryId), name: detail.name, subtitle: detail.subtitle ?? "", images: [detail.mainImageUrl, ...(detail.imageUrls ?? [])].filter((url, index, all): url is string => Boolean(url) && all.indexOf(url) === index), description: detail.description ?? "", sort: String(detail.sort ?? 0) });
        setGroups(groupsFromProduct(detail));
        setRows(rowsFromProduct(detail));
      }
      const stored = window.localStorage.getItem(storageKey);
      if (stored) setRecoveryDraft(JSON.parse(stored) as MerchantProductDraft);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品编辑器加载失败");
    } finally {
      setLoading(false);
    }
  }

  function changed(action: () => void) {
    action();
    setDirty(true);
  }

  function restoreDraft() {
    if (!recoveryDraft) return;
    setForm(recoveryDraft.form);
    setGroups(recoveryDraft.groups);
    setRows(recoveryDraft.rows);
    setRecoveryDraft(null);
    setDirty(true);
  }

  function discardDraft() {
    window.localStorage.removeItem(storageKey);
    setRecoveryDraft(null);
  }

  function syncRows(nextGroups: SpecGroup[], sourceRows = rows) {
    const previous = new Map(sourceRows.map((row) => [specKey(row.specs), row]));
    const generated = combinations(nextGroups);
    const nextRows = generated.length ? generated.map((specs, index) => {
      const exact = previous.get(specKey(specs));
      if (exact) return exact;
      const inherited = sourceRows.find((row) => row.specs.every((oldSpec) => specs.some((spec) => spec.name === oldSpec.name && spec.value === oldSpec.value)));
      return inherited
        ? { ...inherited, id: undefined, specs, code: form.code ? `${form.code}-${index + 1}` : inherited.code, sort: String(index) }
        : { ...EMPTY_SKU, specs, code: form.code ? `${form.code}-${index + 1}` : "", sort: String(index) };
    }) : sourceRows;
    setGroups(nextGroups);
    setRows(nextRows);
    setDirty(true);
  }

  function addGroup() {
    syncRows([...groups, { id: `group-${Date.now()}`, name: "", values: [] }]);
  }

  function renameGroup(group: SpecGroup, name: string) {
    const transformed = rows.map((row) => ({ ...row, specs: row.specs.map((spec) => spec.name === group.name ? { ...spec, name } : spec) }));
    syncRows(groups.map((item) => item.id === group.id ? { ...item, name } : item), transformed);
  }

  function addValue(group: SpecGroup) {
    const value = (valueDrafts[group.id] ?? "").trim();
    if (!value || group.values.includes(value)) return;
    setValueDrafts((current) => ({ ...current, [group.id]: "" }));
    syncRows(groups.map((item) => item.id === group.id ? { ...item, values: [...item.values, value] } : item));
  }

  function removeValue(group: SpecGroup, value: string) {
    syncRows(groups.map((item) => item.id === group.id ? { ...item, values: item.values.filter((itemValue) => itemValue !== value) } : item));
  }

  function removeGroup(group: SpecGroup) {
    const transformed = rows.map((row) => ({ ...row, specs: row.specs.filter((spec) => spec.name !== group.name) }));
    syncRows(groups.filter((item) => item.id !== group.id), transformed);
  }

  function updateRow(index: number, changes: Partial<SkuRow>) {
    changed(() => setRows((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, ...changes } : row)));
  }

  function applyBulk() {
    changed(() => setRows((current) => current.map((row) => ({ ...row, priceYuan: bulk.priceYuan || row.priceYuan, marketPriceYuan: bulk.marketPriceYuan || row.marketPriceYuan, stock: bulk.stock || row.stock }))));
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!store || !form.categoryId || !form.name.trim() || !form.images.length || !form.description.trim() || !rows.length) {
      setError("请完整填写分类、名称、图片、描述，并至少生成一个 SKU");
      return;
    }
    if (!productId && form.code.trim().length < 2) {
      setError("商品编码至少需要 2 个字符");
      return;
    }
    if (rows.some((row) => !row.code.trim() || !Number.isFinite(Number(row.priceYuan)) || Number(row.priceYuan) < 0 || Number(row.stock) < 0)) {
      setError("每个 SKU 都需要编码、有效价格和非负库存");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload: ProductSaveInput = {
        storeId: store.id,
        categoryId: Number(form.categoryId),
        name: form.name.trim(),
        subtitle: form.subtitle.trim(),
        mainImageUrl: form.images[0],
        imageUrls: form.images.slice(1),
        description: form.description.trim(),
        sort: Number(form.sort),
        skus: rows.map((row, index): ProductSkuInput => ({ id: row.id, code: row.code.trim(), specificationValues: row.specs, imageUrl: row.imageUrl, price: Math.round(Number(row.priceYuan) * 100), marketPrice: Math.round(Number(row.marketPriceYuan || 0) * 100), stock: Number(row.stock), status: Number(row.status), sort: Number(row.sort || index) })),
      };
      if (productId) await adminProductApi.updateOwn({ ...payload, id: productId });
      else await adminProductApi.createOwn({ ...payload, code: form.code.trim() });
      window.localStorage.removeItem(storageKey);
      setRecoveryDraft(null);
      setDirty(false);
      router.push("/merchant/products");
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品保存失败");
    } finally {
      setSaving(false);
    }
  }

  const imageOptions = useMemo(() => form.images, [form.images]);

  if (loading) return <div className="editor-skeleton"><span /><span /><span /></div>;

  return (
    <section className="content-section merchant-product-editor">
      <div className="section-heading"><div><p className="eyebrow">MERCHANT · PRODUCT EDITOR</p><h1>{productId ? "编辑商品" : "创建商品"}</h1><p>先保存草稿，确认商品资料和 SKU 后再返回列表提交审核。</p></div><Link className="button button--secondary button--icon-label" href="/merchant/products"><ArrowLeft aria-hidden="true" size={16} />返回商品列表</Link></div>
      {recoveryDraft ? <div className="draft-recovery"><div><strong>发现本机未保存的商品草稿</strong><p>可以恢复上次编辑内容，也可以忽略并继续使用当前版本。</p></div><div className="inline-actions"><button className="button button--secondary button--small" type="button" onClick={discardDraft}>忽略草稿</button><button className="button button--primary button--small" type="button" onClick={restoreDraft}>恢复草稿</button></div></div> : null}
      {error ? <Notice>{error}</Notice> : null}
      <form className="merchant-product-form" onSubmit={save}>
        <nav className="editor-section-nav" aria-label="商品编辑分区"><a href="#product-basic">基本信息</a><a href="#product-media">图片与详情</a><a href="#product-specs">规格与 SKU</a></nav>
        <section className="editor-section" id="product-basic"><div className="editor-section__heading"><span>1</span><div><h2>基本信息</h2><p>商品归类、标题和展示顺序。</p></div></div><div className="editor-section__body"><div className="admin-form-grid">{!productId ? <label className="field"><span>商品编码</span><input maxLength={64} value={form.code} onChange={(event) => changed(() => setForm({ ...form, code: event.target.value }))} placeholder="例如 mug-001" /></label> : <div className="form-readonly">商品编码：{form.code}</div>}<label className="field"><span>商品名称</span><input maxLength={128} required value={form.name} onChange={(event) => changed(() => setForm({ ...form, name: event.target.value }))} /></label><label className="field"><span>分类</span><select required value={form.categoryId} onChange={(event) => changed(() => setForm({ ...form, categoryId: event.target.value }))}><option value="">请选择分类</option>{categories.map(({ node, depth }) => <option key={node.id} value={node.id}>{"　".repeat(depth)}{node.name}</option>)}</select></label><label className="field"><span>副标题</span><input maxLength={255} value={form.subtitle} onChange={(event) => changed(() => setForm({ ...form, subtitle: event.target.value }))} /></label><label className="field"><span>排序</span><input min="0" type="number" value={form.sort} onChange={(event) => changed(() => setForm({ ...form, sort: event.target.value }))} /></label></div></div></section>
        <section className="editor-section" id="product-media"><div className="editor-section__heading"><span>2</span><div><h2>图片与详情</h2><p>第一张图片作为商品主图。</p></div></div><div className="editor-section__body"><ImageUploader value={form.images} directory="merchant-product" audience="admin" onChange={(images) => changed(() => setForm({ ...form, images }))} onError={setError} /><label className="field merchant-description"><span>商品描述</span><textarea maxLength={100000} required rows={10} value={form.description} onChange={(event) => changed(() => setForm({ ...form, description: event.target.value }))} /></label></div></section>
        <section className="editor-section" id="product-specs"><div className="editor-section__heading"><span>3</span><div><h2>规格与 SKU</h2><p>维护规格组，系统自动生成所有组合。</p></div></div><div className="editor-section__body"><div className="spec-builder"><div className="spec-builder__heading"><div><strong>规格组</strong><span>没有多规格时可直接维护一个默认 SKU。</span></div><button className="button button--secondary button--small button--icon-label" type="button" onClick={addGroup}><Plus aria-hidden="true" size={15} />添加规格组</button></div>{groups.map((group) => <div className="spec-group" key={group.id}><div className="spec-group__name"><input aria-label="规格组名称" maxLength={20} value={group.name} onChange={(event) => renameGroup(group, event.target.value)} placeholder="例如 颜色" /><button aria-label="删除规格组" className="icon-button icon-button--danger" title="删除规格组" type="button" onClick={() => removeGroup(group)}><Trash2 aria-hidden="true" size={15} /></button></div><div className="spec-values">{group.values.map((value) => <span key={value}>{value}<button aria-label={`删除规格值 ${value}`} type="button" onClick={() => removeValue(group, value)}><X aria-hidden="true" size={12} /></button></span>)}<input aria-label={`${group.name || "规格"}的新值`} value={valueDrafts[group.id] ?? ""} onChange={(event) => setValueDrafts((current) => ({ ...current, [group.id]: event.target.value }))} onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); addValue(group); } }} placeholder="输入值后回车" /><button type="button" onClick={() => addValue(group)}>添加</button></div></div>)}</div><div className="sku-matrix-toolbar"><div><strong>SKU 矩阵</strong><span>{rows.length} 个组合</span></div><div className="sku-bulk-fields"><input aria-label="批量售价" inputMode="decimal" placeholder="售价（元）" value={bulk.priceYuan} onChange={(event) => setBulk({ ...bulk, priceYuan: event.target.value })} /><input aria-label="批量划线价" inputMode="decimal" placeholder="划线价（元）" value={bulk.marketPriceYuan} onChange={(event) => setBulk({ ...bulk, marketPriceYuan: event.target.value })} /><input aria-label="批量库存" inputMode="numeric" placeholder="库存" value={bulk.stock} onChange={(event) => setBulk({ ...bulk, stock: event.target.value })} /><button className="button button--secondary button--small" type="button" onClick={applyBulk}>批量应用</button></div></div>{!rows.length ? <Notice>请为每个规格组至少添加一个规格值。</Notice> : <div className="sku-matrix"><div className="sku-matrix__head"><span>规格</span><span>SKU 编码</span><span>售价（元）</span><span>划线价（元）</span><span>库存</span><span>图片</span><span>状态</span></div>{rows.map((row, index) => <div className="sku-matrix__row" key={specKey(row.specs) || "default"}><div className="sku-spec-cell">{row.specs.length ? row.specs.map((spec) => <span key={`${spec.name}-${spec.value}`}>{spec.name}：{spec.value}</span>) : <span>默认规格</span>}</div><input aria-label={`SKU ${index + 1} 编码`} maxLength={64} value={row.code} onChange={(event) => updateRow(index, { code: event.target.value })} /><input aria-label={`SKU ${index + 1} 售价`} min="0" step="0.01" type="number" value={row.priceYuan} onChange={(event) => updateRow(index, { priceYuan: event.target.value })} /><input aria-label={`SKU ${index + 1} 划线价`} min="0" step="0.01" type="number" value={row.marketPriceYuan} onChange={(event) => updateRow(index, { marketPriceYuan: event.target.value })} /><input aria-label={`SKU ${index + 1} 库存`} min="0" type="number" value={row.stock} onChange={(event) => updateRow(index, { stock: event.target.value })} /><select aria-label={`SKU ${index + 1} 图片`} value={row.imageUrl} onChange={(event) => updateRow(index, { imageUrl: event.target.value })}><option value="">使用商品主图</option>{imageOptions.map((url, imageIndex) => <option key={url} value={url}>商品图片 {imageIndex + 1}</option>)}</select><select aria-label={`SKU ${index + 1} 状态`} value={row.status} onChange={(event) => updateRow(index, { status: event.target.value })}><option value="0">启用</option><option value="1">停用</option></select></div>)}</div>}</div></section>
        <div className="editor-sticky-actions"><span>{dirty ? autoSaved ? "修改已自动保存到本机" : "正在保存本地草稿…" : "当前内容已保存"}</span><button className="button button--primary button--icon-label" disabled={saving} type="submit"><Save aria-hidden="true" size={17} />{saving ? "保存中…" : "保存商品草稿"}</button></div>
      </form>
      <ConfirmDialog open={Boolean(pendingHref)} title="离开商品编辑器？" description="尚未提交的修改已自动保存在本机，你可以稍后回来恢复。" confirmLabel="离开页面" dangerous busy={false} onClose={() => setPendingHref(null)} onConfirm={() => { const href = pendingHref; setDirty(false); setPendingHref(null); if (href) router.push(href); }} />
    </section>
  );
}
