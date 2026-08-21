"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { communityApi } from "@/lib/api/community";
import { CurmerceApiError } from "@/lib/api/client";
import type { CommunityPost } from "@/lib/types/api";
const statusLabel: Record<number, string> = { 0: "草稿", 1: "已发布", 2: "已隐藏" };
export default function CommunityMinePage() {
  const [posts, setPosts] = useState<CommunityPost[]>([]); const [error, setError] = useState<string | null>(null);
  useEffect(() => { void communityApi.myPage({ pageNo: 1, pageSize: 50 }).then((page) => setPosts(page.list ?? [])).catch((cause) => setError(cause instanceof CurmerceApiError ? cause.message : "帖子加载失败")); }, []);
  return <section className="content-section"><div className="section-heading"><div><p className="eyebrow">COMMUNITY · MINE</p><h1>我的社区帖子</h1><p>草稿和隐藏内容可以继续编辑，发布内容可以查看详情。</p></div><Link className="button button--primary" href="/community/create">新建帖子</Link></div>{error ? <Notice>{error}</Notice> : null}{!error && posts.length === 0 ? <p className="empty-state">还没有帖子。</p> : null}<div className="community-grid">{posts.map((post) => <article className="community-card" key={post.id}><div className="community-card__body"><div className="community-card__meta"><strong>{statusLabel[post.status] ?? `状态 ${post.status}`}</strong><span>#{post.id}</span></div><h2>{post.title}</h2><p>{post.content}</p><div className="inline-actions"><Link className="button button--secondary" href={`/community/${post.id}`}>查看</Link>{post.status !== 1 ? <Link className="button button--primary" href={`/community/create?id=${post.id}`}>编辑</Link> : null}</div></div></article>)}</div></section>;
}
