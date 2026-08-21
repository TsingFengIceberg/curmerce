"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { communityApi } from "@/lib/api/community";
import { CurmerceApiError, assetUrl } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";
import type { CommunityPost } from "@/lib/types/api";
export default function CommunityFollowingPage() {
  const [posts, setPosts] = useState<CommunityPost[]>([]); const [error, setError] = useState<string | null>(null);
  useEffect(() => { void communityApi.following({ pageNo: 1, pageSize: 50 }).then((page) => setPosts(page.list ?? [])).catch((cause) => setError(cause instanceof CurmerceApiError ? cause.message : "关注 Feed 加载失败")); }, []);
  return <section className="content-section"><div className="section-heading"><div><p className="eyebrow">COMMUNITY · FOLLOWING</p><h1>关注 Feed</h1><p>按最新内容查看你关注的作者。</p></div><Link className="button button--secondary" href="/community">发现更多</Link></div>{error ? <Notice>{error}</Notice> : null}{!error && posts.length === 0 ? <p className="empty-state">还没有关注作者的公开帖子。</p> : null}<div className="community-grid">{posts.map((post) => <article className="community-card" key={post.id}><div className="community-card__body"><div className="community-card__meta"><strong>{post.authorNickname || `用户 ${post.authorUserId}`}</strong><span>{formatDateTime(post.createTime)}</span></div><Link href={`/community/${post.id}`}><h2>{post.title}</h2></Link><p>{post.content}</p></div>{post.mediaUrls?.[0] ? <img className="community-card__image" src={assetUrl(post.mediaUrls[0]) ?? ""} alt="帖子配图" /> : null}</article>)}</div></section>;
}
