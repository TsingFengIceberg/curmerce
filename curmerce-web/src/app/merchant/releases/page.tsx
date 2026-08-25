"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Notice } from "@/components/notice";
import { adminProductApi } from "@/lib/api/admin-product";
import { adminReleaseApi } from "@/lib/api/release";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken } from "@/lib/auth/storage";
import { ensureMerchantOwner } from "@/lib/auth/guards";
import { formatDateTime, formatMoney } from "@/lib/format";
import type {
  ProductAdmin,
  ProductSkuAdmin,
  ReleaseCampaign,
  ReleaseCreateInput,
} from "@/lib/types/api";

type ReleaseItemInput = ReleaseCreateInput["items"][number];

const newItem = (): ReleaseItemInput => ({ productId: 0, skuId: 0, campaignPrice: 0, stock: 1 });
const empty: ReleaseCreateInput = {
  name: "",
  startTime: "",
  endTime: "",
  perUserLimit: 1,
  items: [newItem()],
};
const labels: Record<number, string> = {
  0: "草稿",
  10: "待开始",
  20: "进行中",
  30: "已结束",
  40: "已取消",
};

function sellableSkus(product: ProductAdmin | undefined): ProductSkuAdmin[] {
  return product?.skus.filter((sku) => sku.id && sku.status === 0 && sku.stock > 0) ?? [];
}

function skuLabel(sku: ProductSkuAdmin) {
  const specifications = sku.specificationValues
    ?.map((item) => `${item.name}: ${item.value}`)
    .join(" / ");
  return `${sku.code}${specifications ? ` · ${specifications}` : ""} · 库存 ${sku.stock} · ${formatMoney(sku.price)}`;
}

export default function MerchantReleasesPage() {
  const router = useRouter();
  const [form, setForm] = useState<ReleaseCreateInput>({ ...empty, items: [newItem()] });
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [campaigns, setCampaigns] = useState<ReleaseCampaign[]>([]);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void ensureMerchantOwner(router).then((allowed) => {
      if (allowed) void load();
    });
  }, [router]);

  async function load() {
    setLoading(true);
    try {
      const [campaignPage, productPage] = await Promise.all([
        adminReleaseApi.page({ pageNo: 1, pageSize: 50 }),
        adminProductApi.pageOwn({ pageNo: 1, pageSize: 100, auditStatus: 2, saleStatus: 1 }),
      ]);
      setCampaigns(campaignPage?.list ?? []);
      setProducts((productPage?.list ?? []).filter((product) => sellableSkus(product).length > 0));
    } catch (cause) {
      handle(cause, "限时发售活动或商品列表加载失败");
    } finally {
      setLoading(false);
    }
  }

  function handle(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      router.replace("/merchant/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  function update<K extends keyof ReleaseCreateInput>(key: K, value: ReleaseCreateInput[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function updateItem(index: number, changes: Partial<ReleaseItemInput>) {
    setForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) => itemIndex === index ? { ...item, ...changes } : item),
    }));
  }

  function usedSkuIds(exceptIndex: number) {
    return new Set(form.items.filter((_, index) => index !== exceptIndex).map((item) => item.skuId).filter(Boolean));
  }

  function availableSkus(index: number, productId: number) {
    const used = usedSkuIds(index);
    const product = products.find((entry) => entry.id === productId);
    return sellableSkus(product).filter((sku) => sku.id && !used.has(sku.id));
  }

  function availableProducts(index: number) {
    return products.filter((product) => availableSkus(index, product.id).length > 0);
  }

  function selectProduct(index: number, productId: number) {
    const sku = availableSkus(index, productId)[0];
    updateItem(index, {
      productId,
      skuId: sku?.id ?? 0,
      campaignPrice: sku?.price ?? 0,
      stock: sku ? Math.min(Math.max(form.items[index].stock, 1), sku.stock) : 1,
    });
  }

  function selectSku(index: number, skuId: number) {
    const product = products.find((entry) => entry.id === form.items[index].productId);
    const sku = sellableSkus(product).find((entry) => entry.id === skuId);
    updateItem(index, {
      skuId,
      campaignPrice: sku?.price ?? 0,
      stock: sku ? Math.min(Math.max(form.items[index].stock, 1), sku.stock) : 1,
    });
  }

  function selectedSku(item: ReleaseItemInput) {
    return sellableSkus(products.find((product) => product.id === item.productId))
      .find((sku) => sku.id === item.skuId);
  }

  async function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const startTime = new Date(form.startTime).getTime();
    const endTime = new Date(form.endTime).getTime();
    const skuIds = form.items.map((item) => item.skuId);
    const invalidItem = form.items.some((item) => {
      const sku = selectedSku(item);
      return !item.productId || !sku || item.campaignPrice < 0 || item.stock < 1 || item.stock > sku.stock;
    });
    if (!form.name.trim() || !form.startTime || !form.endTime || invalidItem) {
      setError("请完整填写活动信息，并确保活动库存不超过所选 SKU 的可用库存");
      return;
    }
    if (!Number.isFinite(startTime) || startTime <= Date.now()) {
      setError("活动开始时间必须晚于当前时间");
      return;
    }
    if (!Number.isFinite(endTime) || endTime <= startTime) {
      setError("活动结束时间必须晚于开始时间");
      return;
    }
    if (new Set(skuIds).size !== skuIds.length) {
      setError("同一活动不能重复选择相同 SKU");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await adminReleaseApi.create({
        ...form,
        name: form.name.trim(),
        startTime: `${form.startTime}:00`,
        endTime: `${form.endTime}:00`,
      });
      setMessage("限时发售草稿已创建");
      setForm({ ...empty, items: [newItem()] });
      await load();
    } catch (cause) {
      handle(cause, "创建活动失败");
    } finally {
      setBusy(false);
    }
  }

  async function transition(campaign: ReleaseCampaign, action: "publish" | "cancel" | "finish") {
    setBusy(true);
    setError(null);
    try {
      await adminReleaseApi[action](campaign.id);
      setMessage(action === "publish" ? "活动已发布" : action === "cancel" ? "活动已取消" : "活动已结束");
      await load();
    } catch (cause) {
      handle(cause, "活动状态更新失败");
    } finally {
      setBusy(false);
    }
  }

  const totalSellableSkus = products.reduce((total, product) => total + sellableSkus(product).length, 0);

  return (
    <section className="content-section admin-page">
      <div className="section-heading">
        <div><p className="eyebrow">MERCHANT · LIMITED RELEASE</p><h1>限时发售管理</h1><p>从当前商家已上架且有库存的商品中选择活动 SKU。</p></div>
        <div className="inline-actions"><Link className="button button--secondary" href="/merchant/auctions">拍卖管理</Link><Link className="button button--secondary" href="/merchant/products">商品管理</Link></div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <form className="orders-panel product-editor" onSubmit={create}>
        <div className="panel-heading"><h2>创建限时发售草稿</h2><span>金额单位：分</span></div>
        <div className="admin-form-grid">
          <label className="field"><span>活动名称</span><input required value={form.name} onChange={(event) => update("name", event.target.value)} /></label>
          <label className="field"><span>每人限购</span><input min="1" required type="number" value={form.perUserLimit} onChange={(event) => update("perUserLimit", Number(event.target.value))} /></label>
          <label className="field"><span>开始时间</span><input required type="datetime-local" value={form.startTime} onChange={(event) => update("startTime", event.target.value)} /></label>
          <label className="field"><span>结束时间</span><input required type="datetime-local" value={form.endTime} onChange={(event) => update("endTime", event.target.value)} /></label>
        </div>
        <div className="event-form-items">
          {form.items.map((item, index) => {
            const sku = selectedSku(item);
            const productOptions = availableProducts(index);
            const skuOptions = availableSkus(index, item.productId);
            return (
              <div className="event-form-item" key={index}>
                <strong>活动 SKU {index + 1}</strong>
                <label className="field"><span>商品</span><select required value={item.productId || ""} onChange={(event) => selectProduct(index, Number(event.target.value))}><option value="">请选择商品</option>{productOptions.map((product) => <option key={product.id} value={product.id}>{product.name} · {product.code}</option>)}</select></label>
                <label className="field"><span>SKU</span><select required disabled={!item.productId} value={item.skuId || ""} onChange={(event) => selectSku(index, Number(event.target.value))}><option value="">请选择 SKU</option>{skuOptions.map((option) => <option key={option.id} value={option.id}>{skuLabel(option)}</option>)}</select></label>
                <label className="field"><span>活动价</span><input min="0" required type="number" value={item.campaignPrice} onChange={(event) => updateItem(index, { campaignPrice: Number(event.target.value) })} /></label>
                <label className="field"><span>活动库存{sku ? `（最多 ${sku.stock}）` : ""}</span><input min="1" max={sku?.stock} required type="number" value={item.stock} onChange={(event) => updateItem(index, { stock: Number(event.target.value) })} /></label>
                {form.items.length > 1 ? <button className="text-button text-button--danger" type="button" onClick={() => setForm((current) => ({ ...current, items: current.items.filter((_, itemIndex) => itemIndex !== index) }))}>移除 SKU</button> : null}
              </div>
            );
          })}
        </div>
        {!loading && products.length === 0 ? <p className="field-help">当前没有可用于限时发售的已上架有库存商品。</p> : null}
        <div className="inline-actions">
          <button className="button button--secondary" disabled={form.items.length >= totalSellableSkus} type="button" onClick={() => setForm((current) => ({ ...current, items: [...current.items, newItem()] }))}>添加 SKU</button>
          <button className="button button--primary" disabled={busy || loading || products.length === 0} type="submit">{busy ? "创建中…" : "创建草稿"}</button>
        </div>
      </form>
      <div className="orders-panel">
        <div className="panel-heading"><h2>我的活动</h2></div>
        {loading ? <p className="empty-state">活动加载中…</p> : null}
        <div className="admin-record-list">
          {campaigns.map((campaign) => <article className="event-admin-row" key={campaign.id}><div><strong>{campaign.name}</strong><small>{formatDateTime(campaign.startTime)} - {formatDateTime(campaign.endTime)} · {campaign.items.length} 个 SKU</small><p>{campaign.items.map((item) => `${formatMoney(item.campaignPrice)} / 剩余 ${item.stock}`).join("，")}</p></div><div className="inline-actions"><span className="tag">{labels[campaign.status] ?? campaign.status}</span>{campaign.status === 0 || campaign.status === 10 ? <button className="text-button" disabled={busy} type="button" onClick={() => void transition(campaign, "publish")}>发布</button> : null}{campaign.status === 0 || campaign.status === 10 || campaign.status === 20 ? <button className="text-button" disabled={busy} type="button" onClick={() => void transition(campaign, campaign.status === 20 ? "finish" : "cancel")}>{campaign.status === 20 ? "结束" : "取消"}</button> : null}</div></article>)}
        </div>
      </div>
    </section>
  );
}
