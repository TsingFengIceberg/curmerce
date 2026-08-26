"use client";

import { ArrowLeft, Check, PackageSearch, Plus, Save, Send, X } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { ImageUploader } from "@/components/image-uploader";
import { Notice } from "@/components/notice";
import { communityApi } from "@/lib/api/community";
import { catalogApi } from "@/lib/api/catalog";
import { CurmerceApiError } from "@/lib/api/client";
import { formatMoney } from "@/lib/format";
import type { CommunityTopic, PublicProductSummary } from "@/lib/types/api";

type LocalDraft = { title: string; content: string; topics: string[]; mediaUrls: string[]; products: PublicProductSummary[]; savedAt: number };

function CommunityCreateForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const editId = Number(searchParams.get("id") ?? 0);
  const storageKey = `curmerce-community-draft:${editId || "new"}`;
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [topics, setTopics] = useState<string[]>([]);
  const [topicInput, setTopicInput] = useState("");
  const [suggestedTopics, setSuggestedTopics] = useState<CommunityTopic[]>([]);
  const [mediaUrls, setMediaUrls] = useState<string[]>([]);
  const [products, setProducts] = useState<PublicProductSummary[]>([]);
  const [productKeyword, setProductKeyword] = useState("");
  const [productResults, setProductResults] = useState<PublicProductSummary[]>([]);
  const [searchingProducts, setSearchingProducts] = useState(false);
  const [recoveryDraft, setRecoveryDraft] = useState<LocalDraft | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saveState, setSaveState] = useState<"idle" | "saving" | "saved">("idle");
  const [pendingHref, setPendingHref] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoaded(false);
      try {
        const [post, topicList] = await Promise.all([
          editId ? communityApi.get(editId) : Promise.resolve(null),
          communityApi.popularTopics(50),
        ]);
        if (cancelled) return;
        if (post) {
          setTitle(post.title);
          setContent(post.content);
          setTopics(post.topics?.map((topic) => topic.name) ?? []);
          setMediaUrls(post.mediaUrls ?? []);
          setProducts(post.products ?? []);
        }
        setSuggestedTopics(topicList);
        const stored = window.localStorage.getItem(storageKey);
        if (stored) setRecoveryDraft(JSON.parse(stored) as LocalDraft);
      } catch (cause) {
        setError(cause instanceof CurmerceApiError ? cause.message : "编辑器数据加载失败");
      } finally {
        if (!cancelled) setLoaded(true);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [editId, storageKey]);

  useEffect(() => {
    if (!loaded || !dirty) return;
    setSaveState("saving");
    const timer = window.setTimeout(() => {
      const draft: LocalDraft = { title, content, topics, mediaUrls, products, savedAt: Date.now() };
      window.localStorage.setItem(storageKey, JSON.stringify(draft));
      setSaveState("saved");
    }, 900);
    return () => window.clearTimeout(timer);
  }, [loaded, dirty, title, content, topics, mediaUrls, products, storageKey]);

  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
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
    window.addEventListener("beforeunload", beforeUnload);
    document.addEventListener("click", guardLinks, true);
    return () => {
      window.removeEventListener("beforeunload", beforeUnload);
      document.removeEventListener("click", guardLinks, true);
    };
  }, [dirty]);

  function changed(action: () => void) {
    action();
    setDirty(true);
    setMessage(null);
  }

  function addTopic(value: string) {
    const name = value.trim().replace(/^#/, "");
    if (!name || topics.includes(name)) return;
    changed(() => setTopics((current) => [...current, name].slice(0, 10)));
    setTopicInput("");
  }

  function topicKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter" || event.key === "," || event.key === "，") {
      event.preventDefault();
      addTopic(topicInput);
    }
    if (event.key === "Backspace" && !topicInput && topics.length) changed(() => setTopics((current) => current.slice(0, -1)));
  }

  async function searchProducts() {
    if (!productKeyword.trim()) return;
    setSearchingProducts(true);
    setError(null);
    try {
      const page = await catalogApi.productPage({ pageNo: 1, pageSize: 8, keyword: productKeyword.trim(), inStock: true });
      setProductResults(page.list ?? []);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "商品搜索失败");
    } finally {
      setSearchingProducts(false);
    }
  }

  function payload() {
    return { title: title.trim(), content: content.trim(), topics, mediaUrls, productIds: products.map((product) => product.id) };
  }

  async function save(submitAfter: boolean) {
    if (!title.trim() || !content.trim()) {
      setError("请填写标题和正文");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const id = editId || await communityApi.create(payload());
      if (editId) await communityApi.update({ id: editId, ...payload() });
      window.localStorage.removeItem(storageKey);
      setDirty(false);
      if (submitAfter) {
        await communityApi.submit(id);
        router.push(`/community/${id}`);
      } else {
        setMessage("草稿已保存到账号");
        if (!editId) router.replace(`/community/create?id=${id}`);
      }
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "帖子保存失败");
    } finally {
      setBusy(false);
    }
  }

  function restoreDraft() {
    if (!recoveryDraft) return;
    setTitle(recoveryDraft.title);
    setContent(recoveryDraft.content);
    setTopics(recoveryDraft.topics);
    setMediaUrls(recoveryDraft.mediaUrls);
    setProducts(recoveryDraft.products);
    setRecoveryDraft(null);
    setDirty(true);
    setMessage("已恢复上次未提交的本地草稿");
  }

  const selectedProductIds = useMemo(() => new Set(products.map((product) => product.id)), [products]);

  if (!loaded) return <div className="editor-skeleton"><span /><span /><span /></div>;

  return (
    <section className="content-section community-editor community-editor--productized">
      <div className="section-heading"><div><p className="eyebrow">COMMUNITY · WRITE</p><h1>{editId ? "编辑帖子" : "发布帖子"}</h1><p>图片和商品关联都是选填，真实、清楚的内容本身就可以成为一篇帖子。</p></div><button className="button button--secondary button--icon-label" type="button" onClick={() => dirty ? setPendingHref("/community/mine") : router.push("/community/mine")}><ArrowLeft aria-hidden="true" size={16} />返回我的帖子</button></div>
      {recoveryDraft ? <div className="draft-recovery"><div><strong>发现未提交的本地草稿</strong><span>保存于 {new Date(recoveryDraft.savedAt).toLocaleString("zh-CN")}</span></div><div className="inline-actions"><button className="text-button" type="button" onClick={() => { window.localStorage.removeItem(storageKey); setRecoveryDraft(null); }}>忽略</button><button className="button button--secondary button--small" type="button" onClick={restoreDraft}>恢复草稿</button></div></div> : null}
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <form className="community-compose" onSubmit={(event) => { event.preventDefault(); void save(true); }}>
        <div className="community-compose__main">
          <label className="field community-title-field"><span>标题</span><input required maxLength={120} value={title} onChange={(event) => changed(() => setTitle(event.target.value))} placeholder="用一句话概括你的分享" /></label>
          <label className="field"><span>正文</span><textarea required maxLength={100000} rows={16} value={content} onChange={(event) => changed(() => setContent(event.target.value))} placeholder="写下真实体验、使用感受或值得分享的细节……" /></label>
          <section className="compose-block"><ImageUploader value={mediaUrls} directory="community" label="帖子配图（选填）" description="第一张作为封面，可拖动排序并点击预览大图。" onChange={(urls) => changed(() => setMediaUrls(urls))} onError={setError} /></section>
        </div>
        <aside className="community-compose__sidebar">
          <section className="compose-side-block"><div className="compose-block__heading"><div><strong>话题</strong><span>最多添加 10 个</span></div></div><div className="topic-input">{topics.map((topic) => <span key={topic}>#{topic}<button aria-label={`移除话题 ${topic}`} type="button" onClick={() => changed(() => setTopics((current) => current.filter((item) => item !== topic)))}><X aria-hidden="true" size={12} /></button></span>)}<input aria-label="添加话题" value={topicInput} onChange={(event) => setTopicInput(event.target.value)} onKeyDown={topicKeyDown} placeholder={topics.length ? "继续添加" : "搜索或创建话题"} /></div>{suggestedTopics.length ? <div className="topic-suggestions"><span>{topicInput.trim() ? "匹配话题" : "热门话题"}</span><div>{suggestedTopics.filter((topic) => !topics.includes(topic.name) && (!topicInput.trim() || topic.name.toLowerCase().includes(topicInput.trim().toLowerCase()))).slice(0, 8).map((topic) => <button type="button" key={topic.id} onClick={() => addTopic(topic.name)}>#{topic.name}</button>)}</div></div> : null}</section>
          <section className="compose-side-block"><div className="compose-block__heading"><div><strong>关联商品</strong><span>选填，帮助读者继续了解和购买。</span></div></div><div className="product-picker-search"><PackageSearch aria-hidden="true" size={16} /><input aria-label="搜索关联商品" value={productKeyword} onChange={(event) => setProductKeyword(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); void searchProducts(); } }} placeholder="搜索商品名称" /><button type="button" onClick={() => void searchProducts()}>{searchingProducts ? "搜索中" : "搜索"}</button></div>{productResults.length ? <div className="product-picker-results">{productResults.map((product) => <button disabled={selectedProductIds.has(product.id)} key={product.id} type="button" onClick={() => changed(() => setProducts((current) => [...current, product]))}><span><strong>{product.name}</strong><small>{product.storeName || "个人卖家"} · {formatMoney(product.minPrice)}</small></span>{selectedProductIds.has(product.id) ? <Check aria-hidden="true" size={15} /> : <Plus aria-hidden="true" size={15} />}</button>)}</div> : null}{products.length ? <div className="selected-products">{products.map((product) => <div key={product.id}><span><strong>{product.name}</strong><small>{formatMoney(product.minPrice)}</small></span><button aria-label={`移除关联商品 ${product.name}`} type="button" onClick={() => changed(() => setProducts((current) => current.filter((item) => item.id !== product.id)))}><X aria-hidden="true" size={14} /></button></div>)}</div> : <p className="compose-empty-hint">可以不关联商品直接发布。</p>}</section>
          <div className="compose-save-state" aria-live="polite"><span>{saveState === "saving" ? "正在自动保存到本机…" : saveState === "saved" ? "本地草稿已自动保存" : dirty ? "有尚未保存的修改" : "当前内容已保存"}</span></div>
          <div className="compose-actions"><button className="button button--secondary button--icon-label" disabled={busy} type="button" onClick={() => void save(false)}><Save aria-hidden="true" size={16} />保存草稿</button><button className="button button--primary button--icon-label" disabled={busy} type="submit"><Send aria-hidden="true" size={16} />{busy ? "处理中…" : "保存并发布"}</button></div>
        </aside>
      </form>
      <ConfirmDialog open={Boolean(pendingHref)} title="离开编辑器？" description="尚未保存到账号的修改会保留在本机草稿中，你也可以返回继续编辑。" confirmLabel="离开页面" dangerous busy={false} onClose={() => setPendingHref(null)} onConfirm={() => { const href = pendingHref; setDirty(false); setPendingHref(null); if (href) router.push(href); }} />
    </section>
  );
}

export default function CommunityCreatePage() {
  return <Suspense fallback={<div className="editor-skeleton"><span /><span /><span /></div>}><CommunityCreateForm /></Suspense>;
}
