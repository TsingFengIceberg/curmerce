import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CommunityPostCard } from "@/components/community-post-card";
import type { CommunityPost } from "@/lib/types/api";

const post: CommunityPost = {
  id: 12,
  authorUserId: 7,
  authorNickname: "林间",
  title: "露营杯使用记录",
  content: "实际使用一周后的感受。",
  mediaUrls: [],
  status: 1,
  likeCount: 3,
  favoriteCount: 2,
  commentCount: 4,
  liked: false,
  favorited: true,
  followingAuthor: false,
  topics: [],
  products: [],
  createTime: "2026-08-26T08:00:00",
};

describe("CommunityPostCard", () => {
  it("exposes direct like, favorite, follow and comment actions", () => {
    const onReaction = vi.fn();
    const onFollow = vi.fn();
    render(<CommunityPostCard post={post} onFollow={onFollow} onReaction={onReaction} />);

    fireEvent.click(screen.getByRole("button", { name: "点赞" }));
    fireEvent.click(screen.getByRole("button", { name: "取消收藏" }));
    fireEvent.click(screen.getByRole("button", { name: "关注 林间" }));

    expect(onReaction).toHaveBeenNthCalledWith(1, post, 1, true);
    expect(onReaction).toHaveBeenNthCalledWith(2, post, 2, false);
    expect(onFollow).toHaveBeenCalledWith(post);
    expect(screen.getByRole("link", { name: "4 条评论" })).toHaveAttribute("href", "/community/12#comments");
  });
});
