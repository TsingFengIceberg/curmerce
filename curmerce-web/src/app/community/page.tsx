"use client";

import { Compass, Search, UserPlus, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { CommunityPostCard } from "@/components/community-post-card";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { communityApi } from "@/lib/api/community";
import { CurmerceApiError } from "@/lib/api/client";
import type { CommunityPost, CommunityTopic } from "@/lib/types/api";

const PAGE_SIZE = 18;

export default function CommunityPage() {
  const router = useRouter();
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [popularTopics, setPopularTopics] = useState<CommunityTopic[]>([]);
  const [keywordDraft, setKeywordDraft] = useState("");
  const [keyword, setKeyword] = useState("");
  const [topicSlug, setTopicSlug] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const [reactionBusyId, setReactionBusyId] = useState<number | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const initialKeyword = params.get("q") ?? "";
    setKeywordDraft(initialKeyword);
    setKeyword(initialKeyword);
    setTopicSlug(params.get("topic") ?? params.get("topicSlug") ?? "");
    setPageNo(Math.max(1, Number(params.get("page") ?? 1) || 1));
    setHydrated(true);
    void communityApi.popularTopics(12).then(setPopularTopics).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    const params = new URLSearchParams();
    if (keyword) params.set("q", keyword);
    if (topicSlug) params.set("topic", topicSlug);
    if (pageNo > 1) params.set("page", String(pageNo));
    window.history.replaceState(null, "", params.size ? `/community?${params.toString()}` : "/community");
    void load();
  }, [hydrated, keyword, topicSlug, pageNo]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await communityApi.page({ pageNo, pageSize: PAGE_SIZE, keyword, topicSlug });
      setPosts(page.list ?? []);
      setTotal(page.total ?? 0);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "社区内容加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function follow(post: CommunityPost) {
    try {
      await communityApi.follow({ userId: post.authorUserId, active: true });
      setPosts((current) => current.map((item) => item.authorUserId === post.authorUserId ? { ...item, followingAuthor: true } : item));
    } catch (cause) {
      setError(cause instanceof CurmerceApiError && cause.status === 401 ? "登录后才能关注作者" : cause instanceof CurmerceApiError ? cause.message : "关注失败");
    }
  }

  async function react(post: CommunityPost, type: 1 | 2, active: boolean) {
    setReactionBusyId(post.id);
    setError(null);
    try {
      await communityApi.reaction({ postId: post.id, type, active });
      setPosts((current) => current.map((item) => item.id === post.id ? {
        ...item,
        liked: type === 1 ? active : item.liked,
        favorited: type === 2 ? active : item.favorited,
        likeCount: type === 1 ? Math.max(0, item.likeCount + (active ? 1 : -1)) : item.likeCount,
        favoriteCount: type === 2 ? Math.max(0, item.favoriteCount + (active ? 1 : -1)) : item.favoriteCount,
      } : item));
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) router.push("/login");
      else setError(cause instanceof CurmerceApiError ? cause.message : "互动操作失败");
    } finally {
      setReactionBusyId(null);
    }
  }

  const activeTopic = useMemo(() => popularTopics.find((topic) => topic.slug === topicSlug), [popularTopics, topicSlug]);

  function clearFilters() {
    setKeywordDraft("");
    setKeyword("");
    setTopicSlug("");
    setPageNo(1);
  }

  return (
    <section className="content-section community-page">
      <div className="community-page-heading community-page-heading--discover"><div><p className="eyebrow">COMMUNITY · DISCOVER</p><h1>发现真实分享</h1><p>从兴趣内容出发，找到帖子里提到的商品、活动和个人闲置。</p></div><span>{total} 篇公开内容</span></div>
      <form className="community-search community-search--productized" onSubmit={(event) => { event.preventDefault(); setKeyword(keywordDraft.trim()); setPageNo(1); }}><Search aria-hidden="true" size={18} /><input aria-label="搜索社区内容" value={keywordDraft} onChange={(event) => setKeywordDraft(event.target.value)} placeholder="搜索标题、正文或感兴趣的内容" /><button className="button button--primary" type="submit">搜索</button></form>
      {popularTopics.length ? <div className="topic-discovery"><span>热门话题</span><div>{popularTopics.map((topic) => <button aria-pressed={topicSlug === topic.slug} className={topicSlug === topic.slug ? "topic-chip topic-chip--active" : "topic-chip"} key={topic.id} type="button" onClick={() => { setTopicSlug(topicSlug === topic.slug ? "" : topic.slug); setPageNo(1); }}>#{topic.name}</button>)}</div></div> : null}
      {keyword || topicSlug ? <div className="active-filter-row"><span>当前筛选：{keyword ? `“${keyword}”` : ""}{keyword && topicSlug ? " · " : ""}{topicSlug ? `#${activeTopic?.name ?? topicSlug}` : ""}</span><button className="text-button button--icon-label" type="button" onClick={clearFilters}><X aria-hidden="true" size={14} />清空筛选</button></div> : null}
      {error ? <Notice>{error}</Notice> : null}
      {loading ? <div className="community-skeleton"><span /><span /><span /><span /></div> : null}
      {!loading && !posts.length ? <EmptyState icon={<Compass aria-hidden="true" size={23} />} title="没有找到匹配的内容" description="可以清空筛选继续发现，或者发布一篇新的分享。" actionLabel="清空筛选" onAction={clearFilters} /> : null}
      {!loading && posts.length ? <div className="community-grid community-grid--productized">{posts.map((post) => <CommunityPostCard post={post} key={post.id} reactionBusy={reactionBusyId === post.id} onFollow={() => void follow(post)} onReaction={(item, type, active) => void react(item, type, active)} />)}</div> : null}
      <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
    </section>
  );
}
