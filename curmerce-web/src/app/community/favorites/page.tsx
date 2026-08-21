"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { communityApi } from "@/lib/api/community";
import { CurmerceApiError, assetUrl } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";
import type { CommunityPost } from "@/lib/types/api";
export default function CommunityFavoritesPage() {
  const [posts, setPosts] = useState<CommunityPost[]>([]); const [error, setError] = useState<string | null>(null);
  useEffect(() => { void communityApi.favorites({ pageNo: 1, pageSize: 50 }).then((page) => setPosts(page.list ?? [])).catch((cause) => setError(cause instanceof CurmerceApiError ? cause.message : "收藏加载失败")); }, []);
  return <section className="content-section"><div className="section-heading"><div><p className="eyebrow">COMMUNITY · SAVED</p><h1>我的收藏</h1><p>保存过的社区内容和其中的商品关联仍然可以继续查看。</p></div><Link className="button button--secondary" href="/community">回到社区</Link></div>{error ? <Notice>{error}</Notice> : null}{!error && posts.length === 0 ? <p className="empty-state">还没有收藏内容。</p> : null}<div className="community-grid">{posts.map((post) => <article className="community-card" key={post.id}><div className="community-card__body"><div className="community-card__meta"><strong>{post.authorNickname || `用户 ${post.authorUserId}`}</strong><span>{formatDateTime(post.createTime)}</span></div><Link href={`/community/${post.id}`}><h2>{post.title}</h2></Link><p>{post.content}</p></div>{post.mediaUrls?.[0] ? <img className="community-card__image" src={assetUrl(post.mediaUrls[0]) ?? ""} alt="帖子配图" /> : null}</article>)}</div></section>;
}
