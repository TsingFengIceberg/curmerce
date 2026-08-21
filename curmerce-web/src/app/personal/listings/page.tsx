"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { catalogApi } from "@/lib/api/catalog";
import { personalApi } from "@/lib/api/personal";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { PersonalListing, PersonalListingInput, PublicCategoryNode } from "@/lib/types/api";

type FormState = PersonalListingInput & { id?: number };

const emptyForm: FormState = {
  categoryId: 0,
  name: "",
  condition: "",
  mainImageUrl: "",
  imageUrls: [],
  description: "",
  price: 0,
};

const auditLabels: Record<number, string> = { 0: "草稿", 1: "待审核", 2: "审核通过", 3: "已驳回" };
const saleLabels: Record<number, string> = { 0: "下架", 1: "上架" };

function flatten(nodes: PublicCategoryNode[], depth = 0): Array<{ node: PublicCategoryNode; depth: number }> {
  return nodes.flatMap((node) => [{ node, depth }, ...flatten(node.children ?? [], depth + 1)]);
}

export default function PersonalListingsPage() {
  const router = useRouter();
  const [listings, setListings] = useState<PersonalListing[]>([]);
  const [categories, setCategories] = useState<Array<{ node: PublicCategoryNode; depth: number }>>([]);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [auditStatus, setAuditStatus] = useState("");
  const [saleStatus, setSaleStatus] = useState("");
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void Promise.all([loadListings(), loadCategories()]);
  }, [router, auditStatus, saleStatus]);

  async function loadListings() {
    setLoading(true);
    try {
      const response = await personalApi.page({ pageNo: 1, pageSize: 50, auditStatus: auditStatus ? Number(auditStatus) : undefined, saleStatus: saleStatus ? Number(saleStatus) : undefined });
      setListings(response?.list ?? []);
    } catch (cause) {
      handleError(cause, "个人商品加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function loadCategories() {
    try {
      setCategories(flatten((await catalogApi.categoryTree()) ?? []));
    } catch (cause) {
      handleError(cause, "分类加载失败");
    }
  }

  function handleError(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearToken();
      router.replace("/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function startCreate() {
    setEditing(false);
    setForm({ ...emptyForm });
    setError(null);
    setMessage(null);
  }

  async function startEdit(listing: PersonalListing) {
    setBusy(true);
    setError(null);
    try {
      const detail = await personalApi.get(listing.id);
      setForm({ id: detail.id, categoryId: detail.categoryId, name: detail.name, condition: detail.condition, mainImageUrl: detail.mainImageUrl ?? "", imageUrls: detail.imageUrls ?? [], description: detail.description ?? "", price: detail.price ?? 0 });
      setEditing(true);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (cause) {
      handleError(cause, "个人商品详情加载失败");
    } finally {
      setBusy(false);
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input: PersonalListingInput = { ...form, imageUrls: form.imageUrls.map((value) => value.trim()).filter(Boolean), name: form.name.trim(), condition: form.condition.trim(), mainImageUrl: form.mainImageUrl.trim(), description: form.description.trim(), price: Number(form.price) };
    if (!input.categoryId || !input.name || !input.condition || !input.mainImageUrl || !input.description || input.price < 0) {
      setError("请填写分类、名称、成色、主图、描述，价格不能为负数");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const wasEditing = editing && Boolean(form.id);
      if (wasEditing && form.id) await personalApi.update({ ...input, id: form.id });
      else await personalApi.create(input);
      startCreate();
      setMessage(wasEditing ? "个人商品草稿已保存" : "个人商品草稿已创建");
      await loadListings();
    } catch (cause) {
      handleError(cause, "保存个人商品失败");
    } finally {
      setBusy(false);
    }
  }

  async function transition(listing: PersonalListing, action: "submit" | "list" | "delist") {
    setBusyId(listing.id);
    setError(null);
    setMessage(null);
    try {
      if (action === "submit") await personalApi.submit(listing.id);
      if (action === "list") await personalApi.list(listing.id);
      if (action === "delist") await personalApi.delist(listing.id);
      setMessage(action === "submit" ? "商品已提交审核" : action === "list" ? "商品已上架" : "商品已下架");
      await loadListings();
    } catch (cause) {
      handleError(cause, "商品状态更新失败");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="content-section admin-page">
      <div className="section-heading">
        <div><p className="eyebrow">PERSONAL SELLER · LISTINGS</p><h1>我的闲置商品</h1><p>发布一件一库存的个人商品，审核通过后即可上架出售。</p></div>
        <div className="inline-actions"><Link className="button button--secondary" href="/personal/orders">待发货订单</Link><Link className="button button--secondary" href="/catalog">查看商城</Link></div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <form className="orders-panel product-editor" onSubmit={save}>
        <div className="panel-heading"><h2>{editing ? "编辑个人商品草稿" : "发布个人商品"}</h2>{editing ? <button className="text-button" type="button" onClick={startCreate}>取消编辑</button> : null}</div>
        <div className="admin-form-grid">
          <label className="field"><span>商品名称</span><input maxLength={128} required value={form.name} onChange={(event) => update("name", event.target.value)} /></label>
          <label className="field"><span>成色</span><input maxLength={32} required placeholder="例如：九成新" value={form.condition} onChange={(event) => update("condition", event.target.value)} /></label>
          <label className="field"><span>分类</span><select required value={form.categoryId || ""} onChange={(event) => update("categoryId", Number(event.target.value))}><option value="">请选择分类</option>{categories.map(({ node, depth }) => <option key={node.id} value={node.id}>{"　".repeat(depth)}{node.name}</option>)}</select></label>
          <label className="field"><span>价格（分）</span><input min="0" required type="number" value={form.price} onChange={(event) => update("price", Number(event.target.value))} /></label>
          <label className="field"><span>主图地址</span><input maxLength={1024} required value={form.mainImageUrl} onChange={(event) => update("mainImageUrl", event.target.value)} /></label>
        </div>
        <label className="field"><span>更多图片地址（每行一个）</span><textarea rows={3} value={form.imageUrls.join("\n")} onChange={(event) => update("imageUrls", event.target.value.split(/[\n,]/))} /></label>
        <label className="field"><span>商品描述</span><textarea maxLength={100000} required rows={5} value={form.description} onChange={(event) => update("description", event.target.value)} /></label>
        <button className="button button--primary" disabled={busy} type="submit">{busy ? "保存中…" : editing ? "保存草稿" : "创建草稿"}</button>
      </form>
      <div className="orders-panel">
        <div className="panel-heading"><h2>商品列表</h2><div className="inline-actions"><select aria-label="审核状态" value={auditStatus} onChange={(event) => setAuditStatus(event.target.value)}><option value="">全部审核状态</option><option value="0">草稿</option><option value="1">待审核</option><option value="2">审核通过</option><option value="3">已驳回</option></select><select aria-label="销售状态" value={saleStatus} onChange={(event) => setSaleStatus(event.target.value)}><option value="">全部销售状态</option><option value="0">下架</option><option value="1">上架</option></select><button className="button button--secondary button--small" type="button" onClick={startCreate}>新建</button></div></div>
        {loading ? <p className="empty-state">商品加载中…</p> : null}
        {!loading && listings.length === 0 ? <p className="empty-state">当前没有个人商品记录。</p> : null}
        <div className="admin-record-list">
          {listings.map((listing) => <article className="product-admin-card" key={listing.id}><div className="product-admin-card__image">{listing.mainImageUrl ? <img alt={listing.name} src={listing.mainImageUrl} /> : <span>C</span>}</div><div className="product-admin-card__body"><div className="admin-record-card__top"><div><strong>{listing.name}</strong><small>{formatDateTime(listing.updateTime ?? listing.createTime)}</small></div><div className="inline-actions"><span className="tag product-status">{auditLabels[listing.auditStatus] ?? `审核 ${listing.auditStatus}`}</span><span className="tag product-status">{saleLabels[listing.saleStatus] ?? `销售 ${listing.saleStatus}`}</span></div></div><p>{listing.condition} · {listing.description || "暂无描述"}</p><span className="product-admin-card__meta">{listing.stock > 0 ? "一件库存" : "已售出"} · {formatMoney(listing.price ?? 0)}</span><div className="inline-actions"><button className="text-button" type="button" onClick={() => void startEdit(listing)}>编辑</button>{listing.auditStatus === 0 || listing.auditStatus === 3 ? <button className="text-button" disabled={busyId === listing.id} type="button" onClick={() => void transition(listing, "submit")}>提交审核</button> : null}{listing.auditStatus === 2 && listing.saleStatus === 0 && listing.stock > 0 ? <button className="text-button" disabled={busyId === listing.id} type="button" onClick={() => void transition(listing, "list")}>上架</button> : null}{listing.saleStatus === 1 ? <button className="text-button" disabled={busyId === listing.id} type="button" onClick={() => void transition(listing, "delist")}>下架</button> : null}</div>{listing.rejectReason ? <small className="field-help">驳回原因：{listing.rejectReason}</small> : null}</div></article>)}
        </div>
      </div>
    </section>
  );
}
