"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Notice } from "@/components/notice";
import { adminAuctionApi } from "@/lib/api/auction";
import { adminProductApi } from "@/lib/api/admin-product";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { AuctionCreateInput, AuctionSession, ProductAdmin, ProductSkuAdmin } from "@/lib/types/api";

const empty: AuctionCreateInput = { name: "", productId: 0, skuId: 0, startingPrice: 0, minIncrement: 1, startTime: "", endTime: "" };
const labels: Record<number, string> = { 0: "草稿", 10: "待开始", 20: "进行中", 30: "已结束", 40: "已取消", 50: "结算失败" };

function sellableSkus(product: ProductAdmin | undefined): ProductSkuAdmin[] {
  return product?.skus.filter((sku) => sku.id && sku.status === 0 && sku.stock > 0) ?? [];
}

function skuLabel(sku: ProductSkuAdmin) {
  const specifications = sku.specificationValues?.map((item) => `${item.name}: ${item.value}`).join(" / ");
  return `${sku.code}${specifications ? ` · ${specifications}` : ""} · 库存 ${sku.stock} · ${formatMoney(sku.price)}`;
}

export default function MerchantAuctionsPage() {
  const router = useRouter();
  const [form, setForm] = useState<AuctionCreateInput>({ ...empty });
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [items, setItems] = useState<AuctionSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAdminAccessToken()) {
      router.replace("/merchant/login");
      return;
    }
    void load();
  }, [router]);

  async function load() {
    setLoading(true);
    try {
      const [auctionPage, productPage] = await Promise.all([
        adminAuctionApi.page({ pageNo: 1, pageSize: 50 }),
        adminProductApi.pageOwn({ pageNo: 1, pageSize: 100, auditStatus: 2, saleStatus: 1 }),
      ]);
      setItems(auctionPage?.list ?? []);
      setProducts((productPage?.list ?? []).filter((product) => sellableSkus(product).length > 0));
    } catch (cause) {
      handle(cause, "拍卖或商品列表加载失败");
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

  function update<K extends keyof AuctionCreateInput>(key: K, value: AuctionCreateInput[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function selectProduct(productId: number) {
    const product = products.find((item) => item.id === productId);
    const sku = sellableSkus(product)[0];
    setForm((current) => ({ ...current, productId, skuId: sku?.id ?? 0, startingPrice: sku?.price ?? current.startingPrice }));
  }

  async function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.name.trim() || !form.productId || !form.skuId || !form.startTime || !form.endTime) {
      setError("请完整填写拍卖名称、商品、SKU 和时间");
      return;
    }
    const startTime = new Date(form.startTime).getTime();
    const endTime = new Date(form.endTime).getTime();
    if (!Number.isFinite(startTime) || startTime <= Date.now()) {
      setError("拍卖开始时间必须晚于当前时间");
      return;
    }
    if (!Number.isFinite(endTime) || endTime <= startTime) {
      setError("拍卖结束时间必须晚于开始时间");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await adminAuctionApi.create({ ...form, name: form.name.trim(), startTime: `${form.startTime}:00`, endTime: `${form.endTime}:00` });
      setMessage("拍卖草稿已创建");
      setForm({ ...empty });
      await load();
    } catch (cause) {
      handle(cause, "创建拍卖失败");
    } finally {
      setBusy(false);
    }
  }

  async function transition(session: AuctionSession, action: "publish" | "cancel" | "end") {
    setBusy(true);
    setError(null);
    try {
      await adminAuctionApi[action](session.id);
      setMessage(action === "publish" ? "拍卖已发布" : action === "cancel" ? "拍卖已取消" : "拍卖已结束");
      await load();
    } catch (cause) {
      handle(cause, "拍卖状态更新失败");
    } finally {
      setBusy(false);
    }
  }

  const selectedProduct = products.find((product) => product.id === form.productId);
  const availableSkus = sellableSkus(selectedProduct);

  return (
    <section className="content-section admin-page">
      <div className="section-heading">
        <div><p className="eyebrow">MERCHANT · AUCTION</p><h1>拍卖管理</h1><p>从当前商家已上架且有库存的商品中选择拍卖 SKU。</p></div>
        <div className="inline-actions"><Link className="button button--secondary" href="/merchant/releases">限时发售管理</Link><Link className="button button--secondary" href="/auctions">公开拍卖</Link></div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <form className="orders-panel product-editor" onSubmit={create}>
        <div className="panel-heading"><h2>创建拍卖草稿</h2><span>金额单位：分</span></div>
        <div className="admin-form-grid">
          <label className="field"><span>拍卖名称</span><input required value={form.name} onChange={(event) => update("name", event.target.value)} /></label>
          <label className="field"><span>商品</span><select required value={form.productId || ""} onChange={(event) => selectProduct(Number(event.target.value))}><option value="">请选择商品</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name} · {product.code}</option>)}</select></label>
          <label className="field"><span>SKU</span><select required disabled={!selectedProduct} value={form.skuId || ""} onChange={(event) => update("skuId", Number(event.target.value))}><option value="">请选择 SKU</option>{availableSkus.map((sku) => <option key={sku.id} value={sku.id}>{skuLabel(sku)}</option>)}</select></label>
          <label className="field"><span>起拍价</span><input min="0" required type="number" value={form.startingPrice} onChange={(event) => update("startingPrice", Number(event.target.value))} /></label>
          <label className="field"><span>最低加价</span><input min="1" required type="number" value={form.minIncrement} onChange={(event) => update("minIncrement", Number(event.target.value))} /></label>
          <label className="field"><span>开始时间</span><input required type="datetime-local" value={form.startTime} onChange={(event) => update("startTime", event.target.value)} /></label>
          <label className="field"><span>结束时间</span><input required type="datetime-local" value={form.endTime} onChange={(event) => update("endTime", event.target.value)} /></label>
        </div>
        {!loading && products.length === 0 ? <p className="field-help">当前没有可用于拍卖的已上架有库存商品。</p> : null}
        <button className="button button--primary" disabled={busy || loading || products.length === 0} type="submit">{busy ? "创建中…" : "创建草稿"}</button>
      </form>
      <div className="orders-panel">
        <div className="panel-heading"><h2>我的拍卖场次</h2></div>
        {loading ? <p className="empty-state">拍卖加载中…</p> : null}
        <div className="admin-record-list">{items.map((session) => <article className="event-admin-row" key={session.id}><div><strong>{session.name}</strong><small>{formatDateTime(session.startTime)} - {formatDateTime(session.endTime)}</small><p>当前价：{session.currentAmount == null ? "暂无出价" : formatMoney(session.currentAmount)} · 起拍 {formatMoney(session.startingPrice)}</p></div><div className="inline-actions"><span className="tag">{labels[session.status] ?? session.status}</span>{session.status === 0 || session.status === 10 ? <button className="text-button" disabled={busy} type="button" onClick={() => void transition(session, "publish")}>发布</button> : null}{session.status === 0 || session.status === 10 ? <button className="text-button text-button--danger" disabled={busy} type="button" onClick={() => void transition(session, "cancel")}>取消</button> : null}{session.status === 20 ? <button className="text-button" disabled={busy} type="button" onClick={() => void transition(session, "end")}>结束</button> : null}</div></article>)}</div>
      </div>
    </section>
  );
}
