"use client";

import { Bookmark, Compass, FileText, UserRoundCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { CommunityPostCard } from "@/components/community-post-card";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { communityApi } from "@/lib/api/community";
import { CurmerceApiError } from "@/lib/api/client";
import type { ApiPage, CommunityPost } from "@/lib/types/api";

const PAGE_SIZE = 18;
type FeedKind = "following" | "favorites";

const meta = {
  following: { eyebrow: "COMMUNITY · FOLLOWING", title: "关注", description: "按时间查看你关注作者的最新分享。", emptyTitle: "关注流还是空的", emptyDescription: "去发现页关注感兴趣的作者，他们的新帖子会出现在这里。", icon: UserRoundCheck },
  favorites: { eyebrow: "COMMUNITY · SAVED", title: "收藏", description: "重新查看保存过的内容和其中提到的商品。", emptyTitle: "还没有收藏内容", emptyDescription: "在帖子详情点击收藏，内容会集中保存在这里。", icon: Bookmark },
} as const;

export function CommunityFeedPage({ kind }: { kind: FeedKind }) {
  const router = useRouter();
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reactionBusyId, setReactionBusyId] = useState<number | null>(null);
  const current = meta[kind];
  const Icon = current.icon;

  useEffect(() => { void load(); }, [kind, pageNo]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const loader = kind === "following" ? communityApi.following : communityApi.favorites;
      const page: ApiPage<CommunityPost> = await loader({ pageNo, pageSize: PAGE_SIZE });
      setPosts(page.list ?? []);
      setTotal(page.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        router.push("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "社区内容加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function react(post: CommunityPost, type: 1 | 2, active: boolean) {
    setReactionBusyId(post.id);
    setError(null);
    try {
      await communityApi.reaction({ postId: post.id, type, active });
      if (kind === "favorites" && type === 2 && !active) {
        setPosts((currentPosts) => currentPosts.filter((item) => item.id !== post.id));
        setTotal((currentTotal) => Math.max(0, currentTotal - 1));
      } else {
        setPosts((currentPosts) => currentPosts.map((item) => item.id === post.id ? {
          ...item,
          liked: type === 1 ? active : item.liked,
          favorited: type === 2 ? active : item.favorited,
          likeCount: type === 1 ? Math.max(0, item.likeCount + (active ? 1 : -1)) : item.likeCount,
          favoriteCount: type === 2 ? Math.max(0, item.favoriteCount + (active ? 1 : -1)) : item.favoriteCount,
        } : item));
      }
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) router.push("/login");
      else setError(cause instanceof CurmerceApiError ? cause.message : "互动操作失败");
    } finally {
      setReactionBusyId(null);
    }
  }

  return (
    <section className="content-section community-page">
      <div className="community-page-heading"><div><p className="eyebrow">{current.eyebrow}</p><h1>{current.title}</h1><p>{current.description}</p></div><span>{total} 篇内容</span></div>
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <div className="community-skeleton"><span /><span /><span /></div> : null}
      {!loading && !posts.length ? <EmptyState icon={<Icon aria-hidden="true" size={23} />} title={current.emptyTitle} description={current.emptyDescription} action={{ href: "/community", label: "去发现内容" }} /> : null}
      {!loading && posts.length ? <div className="community-grid community-grid--productized">{posts.map((post) => <CommunityPostCard post={post} key={post.id} reactionBusy={reactionBusyId === post.id} onReaction={(item, type, active) => void react(item, type, active)} />)}</div> : null}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
    </section>
  );
}
