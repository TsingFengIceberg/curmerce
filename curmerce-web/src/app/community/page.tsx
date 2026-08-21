"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { communityApi } from "@/lib/api/community";
import { CurmerceApiError, assetUrl } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";
import type { CommunityPost } from "@/lib/types/api";

export default function CommunityPage() {
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [keyword, setKeyword] = useState("");
  const [topicSlug, setTopicSlug] = useState(() => typeof window === "undefined" ? "" : new URLSearchParams(window.location.search).get("topicSlug") ?? "");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  async function load() {
    setLoading(true); setError(null);
    try { setPosts((await communityApi.page({ pageNo: 1, pageSize: 20, keyword, topicSlug })).list ?? []); }
    catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "社区内容加载失败"); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);
  return <section className="content-section community-page">
    <div className="section-heading"><div><p className="eyebrow">COMMUNITY · DISCOVER</p><h1>从真实分享，找到下一件喜欢的东西。</h1><p>浏览兴趣内容，查看关联商品，并在同一条发现路径里完成交易。</p></div><div className="inline-actions"><Link className="button button--secondary" href="/community/mine">我的帖子</Link><Link className="button button--primary" href="/community/create">发布帖子</Link></div></div>
    <form className="community-search" onSubmit={(event) => { event.preventDefault(); void load(); }}><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索帖子标题或内容" /><input value={topicSlug} onChange={(event) => setTopicSlug(event.target.value)} placeholder="按话题 slug 筛选" /><button className="button button--secondary" type="submit">搜索</button></form>
    {error ? <Notice>{error}</Notice> : null}{loading ? <p className="empty-state">社区内容加载中…</p> : null}{!loading && posts.length === 0 ? <p className="empty-state">还没有公开帖子。</p> : null}
    <div className="community-grid">{posts.map((post) => <article className="community-card" key={post.id}><div className="community-card__body"><div className="community-card__meta"><strong>{post.authorNickname || `用户 ${post.authorUserId}`}</strong><span>{formatDateTime(post.createTime)}</span></div><Link href={`/community/${post.id}`}><h2>{post.title}</h2></Link><p>{post.content}</p><div className="community-topics">{post.topics?.map((topic) => <span className="tag" key={topic.id}>#{topic.name}</span>)}</div></div>{post.mediaUrls?.[0] ? <img className="community-card__image" src={assetUrl(post.mediaUrls[0]) ?? ""} alt="帖子配图" /> : null}<div className="community-card__footer"><span>♥ {post.likeCount}</span><span>★ {post.favoriteCount}</span><span>评论 {post.commentCount}</span><span>{post.products?.length ?? 0} 件关联商品</span></div></article>)}</div>
  </section>;
}
