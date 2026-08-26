"use client";

import { ArrowLeft, Save } from "lucide-react";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { ImageUploader } from "@/components/image-uploader";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { catalogApi } from "@/lib/api/catalog";
import { personalApi } from "@/lib/api/personal";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import type { PersonalListingInput, PublicCategoryNode } from "@/lib/types/api";

const CONDITIONS = ["全新未拆封", "全新未使用", "几乎全新", "九成新", "八成新", "有明显使用痕迹"];

type EditorForm = Omit<PersonalListingInput, "mainImageUrl" | "imageUrls" | "price"> & {
  images: string[];
  priceYuan: string;
};
type LocalListingDraft = { form: EditorForm; savedAt: number };

const EMPTY_FORM: EditorForm = { categoryId: 0, name: "", condition: "九成新", images: [], description: "", priceYuan: "" };

function flatten(nodes: PublicCategoryNode[], depth = 0): Array<{ node: PublicCategoryNode; depth: number }> {
  return nodes.flatMap((node) => [{ node, depth }, ...flatten(node.children ?? [], depth + 1)]);
}

export function PersonalListingEditor({ listingId }: { listingId?: number }) {
  const router = useRouter();
  const storageKey = `curmerce-personal-listing-draft:${listingId ?? "new"}`;
  const [form, setForm] = useState<EditorForm>(EMPTY_FORM);
  const [categories, setCategories] = useState<Array<{ node: PublicCategoryNode; depth: number }>>([]);
  const [loading, setLoading] = useState(Boolean(listingId));
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [autoSaved, setAutoSaved] = useState(false);
  const [recoveryDraft, setRecoveryDraft] = useState<LocalListingDraft | null>(null);
  const [pendingHref, setPendingHref] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void load();
  }, [listingId, router]);

  useEffect(() => {
    if (!loaded || !dirty) return;
    setAutoSaved(false);
    const timer = window.setTimeout(() => {
      window.localStorage.setItem(storageKey, JSON.stringify({ form, savedAt: Date.now() } satisfies LocalListingDraft));
      setAutoSaved(true);
    }, 800);
    return () => window.clearTimeout(timer);
  }, [dirty, form, loaded, storageKey]);

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
    setLoading(Boolean(listingId));
    setLoaded(false);
    setError(null);
    try {
      const [tree, detail] = await Promise.all([catalogApi.categoryTree(), listingId ? personalApi.get(listingId) : Promise.resolve(null)]);
      setCategories(flatten(tree ?? []));
      if (detail) {
        const images = [detail.mainImageUrl, ...(detail.imageUrls ?? [])].filter((url, index, all): url is string => Boolean(url) && all.indexOf(url) === index);
        setForm({ categoryId: detail.categoryId, name: detail.name, condition: detail.condition, images, description: detail.description ?? "", priceYuan: ((detail.price ?? 0) / 100).toFixed(2) });
      }
      const stored = window.localStorage.getItem(storageKey);
      if (stored) setRecoveryDraft(JSON.parse(stored) as LocalListingDraft);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "商品编辑数据加载失败");
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  }

  function update<K extends keyof EditorForm>(key: K, value: EditorForm[K]) {
    setForm((current) => ({ ...current, [key]: value }));
    setDirty(true);
    setMessage(null);
  }

  const pricePreview = useMemo(() => {
    const amount = Number(form.priceYuan);
    return Number.isFinite(amount) && amount >= 0 ? `¥${amount.toFixed(2)}` : "—";
  }, [form.priceYuan]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const yuan = Number(form.priceYuan);
    if (!form.categoryId || !form.name.trim() || !form.condition || !form.description.trim() || !form.images.length || !Number.isFinite(yuan) || yuan < 0) {
      setError("请完整填写分类、名称、成色、图片、描述和有效价格");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const input: PersonalListingInput = {
        categoryId: form.categoryId,
        name: form.name.trim(),
        condition: form.condition,
        mainImageUrl: form.images[0],
        imageUrls: form.images.slice(1),
        description: form.description.trim(),
        price: Math.round(yuan * 100),
      };
      if (listingId) await personalApi.update({ ...input, id: listingId });
      else await personalApi.create(input);
      window.localStorage.removeItem(storageKey);
      setDirty(false);
      router.push("/personal/listings");
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "个人商品保存失败");
    } finally {
      setSaving(false);
    }
  }

  function restoreDraft() {
    if (!recoveryDraft) return;
    setForm(recoveryDraft.form);
    setRecoveryDraft(null);
    setDirty(true);
    setMessage("已恢复上次未提交的本地草稿");
  }

  if (loading) return <div className="editor-skeleton"><span /><span /><span /></div>;

  return (
    <section className="content-section listing-editor-page">
      <div className="section-heading">
        <div><p className="eyebrow">PERSONAL SELLER · EDITOR</p><h1>{listingId ? "编辑闲置商品" : "发布闲置商品"}</h1><p>商品先保存为草稿，返回列表后再提交平台审核。</p></div>
        <button className="button button--secondary button--icon-label" type="button" onClick={() => dirty ? setPendingHref("/personal/listings") : router.push("/personal/listings")}><ArrowLeft aria-hidden="true" size={17} />返回列表</button>
      </div>
      {recoveryDraft ? <div className="draft-recovery"><div><strong>发现未提交的本地草稿</strong><span>保存于 {new Date(recoveryDraft.savedAt).toLocaleString("zh-CN")}</span></div><div className="inline-actions"><button className="text-button" type="button" onClick={() => { window.localStorage.removeItem(storageKey); setRecoveryDraft(null); }}>忽略</button><button className="button button--secondary button--small" type="button" onClick={restoreDraft}>恢复草稿</button></div></div> : null}
      {message ? <Notice tone="success">{message}</Notice> : null}
      {dirty ? <div className="editor-save-state" role="status">{autoSaved ? "修改已自动保存到本机" : "正在保存本地草稿…"}</div> : null}
      {error ? <Notice>{error}</Notice> : null}
      <form className="listing-editor" onSubmit={save}>
        <section className="editor-section"><div className="editor-section__heading"><span>1</span><div><h2>基本信息</h2><p>帮助买家快速理解这件商品。</p></div></div><div className="editor-section__body"><div className="admin-form-grid"><label className="field"><span>商品名称</span><input maxLength={128} required value={form.name} onChange={(event) => update("name", event.target.value)} placeholder="例如：九成新机械键盘" /></label><label className="field"><span>分类</span><select required value={form.categoryId || ""} onChange={(event) => update("categoryId", Number(event.target.value))}><option value="">请选择分类</option>{categories.map(({ node, depth }) => <option key={node.id} value={node.id}>{"　".repeat(depth)}{node.name}</option>)}</select></label><label className="field"><span>成色</span><select required value={form.condition} onChange={(event) => update("condition", event.target.value)}>{CONDITIONS.map((condition) => <option key={condition}>{condition}</option>)}</select></label><label className="field"><span>售价（元）</span><div className="money-input"><span>¥</span><input inputMode="decimal" min="0" required step="0.01" type="number" value={form.priceYuan} onChange={(event) => update("priceYuan", event.target.value)} placeholder="0.00" /></div><small className="field-help">买家看到的价格：{pricePreview}</small></label></div></div></section>
        <section className="editor-section"><div className="editor-section__heading"><span>2</span><div><h2>商品图片</h2><p>封面决定商品在列表中的第一印象。</p></div></div><div className="editor-section__body"><ImageUploader value={form.images} directory="personal-listing" disabled={saving} onChange={(images) => update("images", images)} onError={setError} /></div></section>
        <section className="editor-section"><div className="editor-section__heading"><span>3</span><div><h2>详细描述</h2><p>说明使用情况、瑕疵、附件和交易注意事项。</p></div></div><div className="editor-section__body"><label className="field"><span>商品描述</span><textarea maxLength={100000} required rows={9} value={form.description} onChange={(event) => update("description", event.target.value)} placeholder="建议如实说明购买时间、使用频率、外观瑕疵和包含的附件。" /></label></div></section>
        <div className="editor-sticky-actions"><span>{dirty ? autoSaved ? "修改已自动保存到本机" : "正在保存本地草稿…" : "当前内容已保存"}</span><button className="button button--primary button--icon-label" disabled={saving} type="submit"><Save aria-hidden="true" size={17} />{saving ? "保存中…" : "保存草稿"}</button></div>
      </form>
      <ConfirmDialog open={Boolean(pendingHref)} title="离开编辑器？" description="尚未保存到账号的修改已经保存在本机，你可以稍后回来恢复。" confirmLabel="离开页面" dangerous busy={false} onClose={() => setPendingHref(null)} onConfirm={() => { const href = pendingHref; setDirty(false); setPendingHref(null); if (href) router.push(href); }} />
    </section>
  );
}
