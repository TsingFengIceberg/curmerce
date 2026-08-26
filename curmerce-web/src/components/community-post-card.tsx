"use client";

import { Bookmark, Heart, MessageCircle, PackageSearch, UserPlus } from "lucide-react";
import Link from "next/link";
import { assetUrl } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";
import type { CommunityPost } from "@/lib/types/api";

export function CommunityPostCard({ post, reactionBusy = false, onFollow, onReaction }: { post: CommunityPost; reactionBusy?: boolean; onFollow?: (post: CommunityPost) => void; onReaction?: (post: CommunityPost, type: 1 | 2, active: boolean) => void }) {
  const author = post.authorNickname || `用户 ${post.authorUserId}`;
  return (
    <article className="community-card community-card--productized">
      {post.mediaUrls?.[0] ? <Link className="community-card__cover" href={`/community/${post.id}`}><img src={assetUrl(post.mediaUrls[0]) ?? ""} alt={`${post.title}的封面`} /></Link> : null}
      <div className="community-card__body">
        <div className="community-author-row">
          <span className="community-avatar">{post.authorAvatar ? <img alt={`${author}的头像`} src={assetUrl(post.authorAvatar) ?? ""} /> : author.slice(0, 1)}</span>
          <span className="community-author-row__identity"><strong>{author}</strong><small>{formatDateTime(post.createTime)}</small></span>
          {onFollow && !post.followingAuthor ? <button aria-label={`关注 ${author}`} className="icon-button" title="关注作者" type="button" onClick={() => onFollow(post)}><UserPlus aria-hidden="true" size={15} /></button> : null}
        </div>
        <Link href={`/community/${post.id}`}><h2>{post.title}</h2><p>{post.content}</p></Link>
        {post.topics?.length ? <div className="community-topics">{post.topics.map((topic) => <Link className="tag" href={`/community?topic=${encodeURIComponent(topic.slug)}`} key={topic.id}>#{topic.name}</Link>)}</div> : null}
      </div>
      <div className="community-card__footer community-card__footer--icons">
        <button aria-label={post.liked ? "取消点赞" : "点赞"} aria-pressed={post.liked} className={post.liked ? "is-active" : ""} disabled={reactionBusy} title={post.liked ? "取消点赞" : "点赞"} type="button" onClick={() => onReaction?.(post, 1, !post.liked)}><Heart aria-hidden="true" fill={post.liked ? "currentColor" : "none"} size={15} />{post.likeCount}</button>
        <button aria-label={post.favorited ? "取消收藏" : "收藏"} aria-pressed={post.favorited} className={post.favorited ? "is-active" : ""} disabled={reactionBusy} title={post.favorited ? "取消收藏" : "收藏"} type="button" onClick={() => onReaction?.(post, 2, !post.favorited)}><Bookmark aria-hidden="true" fill={post.favorited ? "currentColor" : "none"} size={15} />{post.favoriteCount}</button>
        <Link aria-label={`${post.commentCount} 条评论`} href={`/community/${post.id}#comments`} title="查看评论"><MessageCircle aria-hidden="true" size={15} />{post.commentCount}</Link>
        {post.products?.length ? <Link aria-label={`${post.products.length} 个关联商品`} href={`/community/${post.id}`} title="查看关联商品"><PackageSearch aria-hidden="true" size={15} />{post.products.length}</Link> : null}
      </div>
    </article>
  );
}
