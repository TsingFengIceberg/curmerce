"use client";

import { FileText, Pencil, Plus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { communityApi } from "@/lib/api/community";
import { CurmerceApiError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";
import type { CommunityPost } from "@/lib/types/api";

const PAGE_SIZE = 15;
const statusLabel: Record<number, string> = { 0: "草稿", 1: "已发布", 2: "已隐藏" };

export default function CommunityMinePage() {
  const router = useRouter();
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => { void load(); }, [pageNo]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await communityApi.myPage({ pageNo, pageSize: PAGE_SIZE });
      setPosts(page.list ?? []);
      setTotal(page.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        router.push("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "帖子加载失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="content-section community-page">
      <div className="community-page-heading"><div><p className="eyebrow">COMMUNITY · MINE</p><h1>我的帖子</h1><p>管理草稿、已发布和被平台隐藏的内容。</p></div><Link className="button button--primary button--icon-label" href="/community/create"><Plus aria-hidden="true" size={16} />发布帖子</Link></div>
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
      {!loading && !posts.length ? <EmptyState icon={<FileText aria-hidden="true" size={23} />} title="还没有发布内容" description="记录一次真实体验，或先保存一篇草稿。" action={{ href: "/community/create", label: "发布第一篇帖子" }} /> : null}
      {!loading && posts.length ? <div className="my-post-list">{posts.map((post) => <article key={post.id}><div><span className="tag">{statusLabel[post.status] ?? `状态 ${post.status}`}</span><strong>{post.title}</strong><p>{post.content}</p><small>更新于 {formatDateTime(post.updateTime ?? post.createTime)}</small></div><div className="inline-actions"><Link className="button button--secondary button--small" href={`/community/${post.id}`}>查看</Link>{post.status !== 1 ? <Link aria-label={`编辑 ${post.title}`} className="icon-button" href={`/community/create?id=${post.id}`} title="编辑帖子"><Pencil aria-hidden="true" size={16} /></Link> : null}</div></article>)}</div> : null}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
    </section>
  );
}
