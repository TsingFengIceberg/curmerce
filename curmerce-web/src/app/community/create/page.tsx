"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { Notice } from "@/components/notice";
import { communityApi } from "@/lib/api/community";
import { CurmerceApiError } from "@/lib/api/client";

export default function CommunityCreatePage() {
  const router = useRouter();
  const [title, setTitle] = useState(""); const [content, setContent] = useState(""); const [topics, setTopics] = useState(""); const [mediaUrls, setMediaUrls] = useState(""); const [productIds, setProductIds] = useState(""); const [error, setError] = useState<string | null>(null); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(null); try { const id = await communityApi.create({ title, content, topics: topics.split(",").map((v) => v.trim()).filter(Boolean), mediaUrls: mediaUrls.split("\n").map((v) => v.trim()).filter(Boolean), productIds: productIds.split(",").map((v) => Number(v.trim())).filter((v) => Number.isInteger(v) && v > 0) }); await communityApi.submit(id); router.push(`/community/${id}`); } catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "帖子发布失败"); } finally { setBusy(false); } }
  return <section className="content-section community-editor"><div className="section-heading"><div><p className="eyebrow">COMMUNITY · WRITE</p><h1>发布一篇帖子</h1><p>可以先保存草稿，提交后会立即进入公开内容流。</p></div></div>{error ? <Notice>{error}</Notice> : null}<form className="form-card" onSubmit={submit}><label className="field"><span>标题</span><input required maxLength={120} value={title} onChange={(event) => setTitle(event.target.value)} /></label><label className="field"><span>正文</span><textarea required maxLength={100000} rows={12} value={content} onChange={(event) => setContent(event.target.value)} /></label><label className="field"><span>话题（逗号分隔）</span><input value={topics} onChange={(event) => setTopics(event.target.value)} placeholder="例如 咖啡,居家" /></label><label className="field"><span>配图 URL（每行一个）</span><textarea rows={3} value={mediaUrls} onChange={(event) => setMediaUrls(event.target.value)} /></label><label className="field"><span>关联商品编号（逗号分隔）</span><input value={productIds} onChange={(event) => setProductIds(event.target.value)} placeholder="例如 18,19" /></label><button className="button button--primary" disabled={busy} type="submit">{busy ? "发布中…" : "保存并发布"}</button></form></section>;
}
