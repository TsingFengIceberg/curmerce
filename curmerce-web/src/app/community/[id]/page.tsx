"use client";

import { Bookmark, ChevronLeft, Eye, Flag, Heart, MessageCircle, MoreHorizontal, Reply, Send, UserCheck, UserPlus, X } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { communityApi } from "@/lib/api/community";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { CommunityComment, CommunityPost } from "@/lib/types/api";

const COMMENT_PAGE_SIZE = 20;

export default function CommunityPostPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const previewRef = useRef<HTMLDialogElement>(null);
  const [post, setPost] = useState<CommunityPost | null>(null);
  const [comments, setComments] = useState<CommunityComment[]>([]);
  const [commentTotal, setCommentTotal] = useState(0);
  const [commentPage, setCommentPage] = useState(1);
  const [comment, setComment] = useState("");
  const [replyTo, setReplyTo] = useState<CommunityComment | null>(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportReason, setReportReason] = useState("");
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const id = Number(params.id);
    if (id > 0) void load(id);
  }, [params.id]);

  async function load(id: number) {
    setError(null);
    try {
      const [nextPost, nextComments] = await Promise.all([communityApi.get(id), communityApi.pageComments(id, 1, COMMENT_PAGE_SIZE)]);
      setPost(nextPost);
      setComments(nextComments.list ?? []);
      setCommentTotal(nextComments.total ?? 0);
      setCommentPage(1);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "帖子加载失败");
    }
  }

  async function loadMoreComments() {
    if (!post) return;
    const nextPage = commentPage + 1;
    try {
      const page = await communityApi.pageComments(post.id, nextPage, COMMENT_PAGE_SIZE);
      setComments((current) => [...current, ...(page.list ?? [])]);
      setCommentPage(nextPage);
      setCommentTotal(page.total ?? commentTotal);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "更多评论加载失败");
    }
  }

  function requireLogin(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      router.push("/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  async function toggle(type: number, active: boolean) {
    if (!post) return;
    try {
      await communityApi.reaction({ postId: post.id, type, active });
      setPost(await communityApi.get(post.id));
    } catch (cause) {
      requireLogin(cause, "操作失败");
    }
  }

  async function toggleFollow() {
    if (!post) return;
    try {
      await communityApi.follow({ userId: post.authorUserId, active: !post.followingAuthor });
      setPost(await communityApi.get(post.id));
    } catch (cause) {
      requireLogin(cause, "关注操作失败");
    }
  }

  async function addComment() {
    if (!post || !comment.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await communityApi.comment({ postId: post.id, parentId: replyTo?.id, content: comment.trim() });
      setComment("");
      setReplyTo(null);
      const page = await communityApi.pageComments(post.id, 1, COMMENT_PAGE_SIZE);
      setComments(page.list ?? []);
      setCommentTotal(page.total ?? 0);
      setCommentPage(1);
      setPost(await communityApi.get(post.id));
      setMessage("评论已发布");
    } catch (cause) {
      requireLogin(cause, "评论失败");
    } finally {
      setBusy(false);
    }
  }

  async function report() {
    if (!post || !reportReason.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await communityApi.report({ postId: post.id, reason: reportReason.trim() });
      setReportReason("");
      setReportOpen(false);
      setMessage("举报已提交，平台会尽快处理");
    } catch (cause) {
      requireLogin(cause, "举报失败");
    } finally {
      setBusy(false);
    }
  }

  function openPreview(url: string) {
    setPreviewUrl(url);
    previewRef.current?.showModal();
  }

  const commentById = useMemo(() => new Map(comments.map((item) => [item.id, item])), [comments]);

  if (!post) return <section className="content-section"><Notice>{error ?? "帖子加载中…"}</Notice></section>;
  const author = post.authorNickname || `用户 ${post.authorUserId}`;

  return (
    <section className="content-section community-detail community-detail--productized">
      <div className="community-detail__back"><Link className="button--icon-label" href="/community"><ChevronLeft aria-hidden="true" size={16} />返回发现</Link><div className="inline-actions">{post.status !== 1 ? <Link href={`/community/create?id=${post.id}`}>编辑帖子</Link> : null}<details className="more-menu"><summary aria-label="更多操作" title="更多操作"><MoreHorizontal aria-hidden="true" size={19} /></summary><div><button className="button--icon-label" type="button" onClick={() => setReportOpen(true)}><Flag aria-hidden="true" size={15} />举报内容</button></div></details></div></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <article className="community-detail__article community-article">
        <header className="community-article__header"><span className="community-avatar community-avatar--large">{post.authorAvatar ? <img alt={`${author}的头像`} src={assetUrl(post.authorAvatar) ?? ""} /> : author.slice(0, 1)}</span><div><strong>{author}</strong><span>{formatDateTime(post.createTime)}</span></div><button className={post.followingAuthor ? "button button--secondary button--small button--icon-label" : "button button--primary button--small button--icon-label"} type="button" onClick={() => void toggleFollow()}>{post.followingAuthor ? <UserCheck aria-hidden="true" size={15} /> : <UserPlus aria-hidden="true" size={15} />}{post.followingAuthor ? "已关注" : "关注"}</button></header>
        <h1>{post.title}</h1>
        {post.topics?.length ? <div className="community-topics">{post.topics.map((topic) => <Link href={`/community?topic=${encodeURIComponent(topic.slug)}`} className="tag" key={topic.id}>#{topic.name}</Link>)}</div> : null}
        <p className="community-detail__content">{post.content}</p>
        {post.mediaUrls?.length ? <div className={`community-media-grid community-media-grid--${Math.min(post.mediaUrls.length, 4)}`}>{post.mediaUrls.map((url, index) => <button aria-label={`查看第 ${index + 1} 张配图`} key={url} type="button" onClick={() => openPreview(url)}><img src={assetUrl(url) ?? ""} alt={`帖子配图 ${index + 1}`} /><span><Eye aria-hidden="true" size={18} /></span></button>)}</div> : null}
        <div className="community-actions community-actions--icons"><button aria-pressed={post.liked} className={post.liked ? "reaction-button reaction-button--active" : "reaction-button"} type="button" onClick={() => void toggle(1, !post.liked)}><Heart aria-hidden="true" fill={post.liked ? "currentColor" : "none"} size={18} />{post.liked ? "已赞" : "点赞"}<span>{post.likeCount}</span></button><button aria-pressed={post.favorited} className={post.favorited ? "reaction-button reaction-button--active" : "reaction-button"} type="button" onClick={() => void toggle(2, !post.favorited)}><Bookmark aria-hidden="true" fill={post.favorited ? "currentColor" : "none"} size={18} />{post.favorited ? "已收藏" : "收藏"}<span>{post.favoriteCount}</span></button><span className="reaction-count"><MessageCircle aria-hidden="true" size={18} />{post.commentCount} 条评论</span></div>
        {post.products?.length ? <div className="community-products community-products--compact"><div className="panel-heading"><h2>帖子中提到的商品</h2><span>{post.products.length} 件</span></div><div className="community-product-strip">{post.products.map((product) => <Link href={`/products/${product.id}`} key={product.id}>{product.mainImageUrl ? <img alt={product.name} src={assetUrl(product.mainImageUrl) ?? ""} /> : <span className="listing-table__placeholder">C</span>}<div><strong>{product.name}</strong><small>{product.storeName || "个人卖家"}</small><b>{formatMoney(product.minPrice)}</b></div></Link>)}</div></div> : null}
      </article>
      <section className="community-comments community-comments--productized" id="comments">
        <div className="panel-heading"><h2>评论</h2><span>{commentTotal} 条</span></div>
        {!comments.length ? <EmptyState icon={<MessageCircle aria-hidden="true" size={22} />} title="还没有评论" description="说说你的真实想法，开启第一段讨论。" /> : null}
        <div className="comment-list comment-list--threaded">{comments.map((item) => { const parent = item.parentId ? commentById.get(item.parentId) : null; const name = item.authorNickname || `用户 ${item.authorUserId}`; return <article className={item.parentId ? "comment-item comment-item--reply" : "comment-item"} key={item.id}><span className="community-avatar">{name.slice(0, 1)}</span><div><header><strong>{name}</strong><span>{formatDateTime(item.createTime)}</span></header>{parent ? <blockquote><strong>{parent.authorNickname || `用户 ${parent.authorUserId}`}</strong>{parent.content}</blockquote> : null}<p>{item.content}</p><button className="text-button button--icon-label" type="button" onClick={() => setReplyTo(item)}><Reply aria-hidden="true" size={14} />回复</button></div></article>; })}</div>
        {comments.length < commentTotal ? <button className="button button--secondary button--full" type="button" onClick={() => void loadMoreComments()}>加载更多评论</button> : null}
        <div className="comment-compose comment-compose--productized">{replyTo ? <div className="reply-context"><span>回复 <strong>{replyTo.authorNickname || `用户 ${replyTo.authorUserId}`}</strong></span><p>{replyTo.content}</p><button aria-label="取消回复" title="取消回复" type="button" onClick={() => setReplyTo(null)}><X aria-hidden="true" size={15} /></button></div> : null}<textarea rows={4} value={comment} onChange={(event) => setComment(event.target.value)} placeholder={replyTo ? "写下你的回复" : "写下你的看法"} /><div><small>{comment.length}/1000</small><button className="button button--primary button--icon-label" disabled={busy || !comment.trim()} type="button" onClick={() => void addComment()}><Send aria-hidden="true" size={15} />发表评论</button></div></div>
      </section>
      <Drawer open={reportOpen} title="举报内容" description="请准确描述问题，平台审核人员会查看完整帖子上下文。" busy={busy} onClose={() => setReportOpen(false)}><div className="drawer-form"><label className="field"><span>举报原因</span><textarea maxLength={500} required rows={7} value={reportReason} onChange={(event) => setReportReason(event.target.value)} placeholder="例如：包含虚假交易信息或不当内容" /></label><div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setReportOpen(false)}>取消</button><button className="button button--danger button--icon-label" disabled={busy || !reportReason.trim()} type="button" onClick={() => void report()}><Flag aria-hidden="true" size={15} />提交举报</button></div></div></Drawer>
      <dialog className="image-preview-dialog" ref={previewRef} onCancel={(event) => { event.preventDefault(); previewRef.current?.close(); }}><button aria-label="关闭预览" className="confirm-dialog__close" title="关闭" type="button" onClick={() => previewRef.current?.close()}><X aria-hidden="true" size={20} /></button>{previewUrl ? <img alt="帖子配图大图预览" src={assetUrl(previewUrl) ?? ""} /> : null}</dialog>
    </section>
  );
}
