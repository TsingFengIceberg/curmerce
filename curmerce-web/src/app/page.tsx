"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { ArrowRight, Gavel, Search, Sparkles, Timer } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { MediaImage } from "@/components/media-image";
import { ProductCard } from "@/components/product-card";
import { catalogApi } from "@/lib/api/catalog";
import { communityApi } from "@/lib/api/community";
import { releaseApi } from "@/lib/api/release";
import { auctionApi } from "@/lib/api/auction";
import { assetUrl } from "@/lib/api/client";
import { formatDateTime, formatMoney } from "@/lib/format";
import type { AuctionSession, CommunityPost, PublicProductSummary, ReleaseCampaign } from "@/lib/types/api";

export default function HomePage() {
  const router = useRouter();
  const [keyword, setKeyword] = useState("");
  const [products, setProducts] = useState<PublicProductSummary[]>([]);
  const [personalProducts, setPersonalProducts] = useState<PublicProductSummary[]>([]);
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [releases, setReleases] = useState<ReleaseCampaign[]>([]);
  const [auctions, setAuctions] = useState<AuctionSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState<Set<string>>(new Set());

  useEffect(() => {
    void loadHome();
  }, []);

  async function loadHome() {
    setLoading(true);
    const results = await Promise.allSettled([
      catalogApi.productPage({ pageNo: 1, pageSize: 8 }),
      catalogApi.productPage({ pageNo: 1, pageSize: 4, sellerType: 2 }),
      communityApi.page({ pageNo: 1, pageSize: 4 }),
      releaseApi.page({ pageNo: 1, pageSize: 2 }),
      auctionApi.page({ pageNo: 1, pageSize: 2 }),
    ]);
    const failed = new Set<string>();
    const [productResult, personalResult, postResult, releaseResult, auctionResult] = results;
    if (productResult.status === "fulfilled") setProducts(productResult.value?.list ?? []); else failed.add("products");
    if (personalResult.status === "fulfilled") setPersonalProducts(personalResult.value?.list ?? []); else failed.add("personal");
    if (postResult.status === "fulfilled") setPosts(postResult.value?.list ?? []); else failed.add("posts");
    if (releaseResult.status === "fulfilled") setReleases(releaseResult.value?.list ?? []); else failed.add("releases");
    if (auctionResult.status === "fulfilled") setAuctions(auctionResult.value?.list ?? []); else failed.add("auctions");
    setErrors(failed);
    setLoading(false);
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = keyword.trim();
    router.push(value ? `/search?keyword=${encodeURIComponent(value)}` : "/search");
  }

  return (
    <div className="discovery-home">
      <section className="discovery-intro">
        <div>
          <p className="eyebrow">CURMERCE DISCOVERY</p>
          <h1>从真实兴趣出发，发现值得带回家的东西。</h1>
          <p>先看社区里的使用体验，再从普通商品、限时发售、拍卖或个人闲置中选择适合自己的交易方式。</p>
        </div>
        <form className="discovery-search" onSubmit={search}>
          <Search aria-hidden="true" size={20} />
          <input aria-label="搜索商品" placeholder="搜索商品名称或描述" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
          <button className="button button--primary" type="submit">全站搜索</button>
        </form>
        <nav className="discovery-shortcuts" aria-label="快捷发现">
          <Link href="/community"><Sparkles aria-hidden="true" size={18} /><span><strong>社区发现</strong><small>看看大家正在分享什么</small></span><ArrowRight aria-hidden="true" size={17} /></Link>
          <Link href="/releases"><Timer aria-hidden="true" size={18} /><span><strong>限时发售</strong><small>在活动时间内抢购</small></span><ArrowRight aria-hidden="true" size={17} /></Link>
          <Link href="/auctions"><Gavel aria-hidden="true" size={18} /><span><strong>拍卖场</strong><small>为稀缺商品公开出价</small></span><ArrowRight aria-hidden="true" size={17} /></Link>
        </nav>
      </section>

      <section className="discovery-section">
        <div className="discovery-section__heading"><div><p className="eyebrow">COMMUNITY FIRST</p><h2>从正在发生的分享开始</h2></div><Link href="/community">进入社区<ArrowRight aria-hidden="true" size={17} /></Link></div>
        {loading ? <div className="content-skeleton-grid">{Array.from({ length: 4 }, (_, index) => <span key={index} />)}</div> : errors.has("posts") ? <EmptyState title="社区分享暂时加载失败" description="商城的其他内容仍可继续浏览。" actionLabel="重新加载" onAction={() => void loadHome()} /> : posts.length === 0 ? <EmptyState title="还没有公开分享" action={{ href: "/community/create", label: "发布第一篇帖子" }} /> : <div className="home-story-grid">{posts.map((post) => <Link className="home-story" href={`/community/${post.id}`} key={post.id}><MediaImage alt="" fallback={<span className="home-story__placeholder"><Sparkles aria-hidden="true" /></span>} src={assetUrl(post.mediaUrls?.[0])} /><div><span>{post.authorNickname || "Curmerce 用户"} · {formatDateTime(post.createTime)}</span><h3>{post.title}</h3><p>{post.content}</p><small>{post.likeCount} 赞 · {post.commentCount} 评论</small></div></Link>)}</div>}
      </section>

      <section className="discovery-section">
        <div className="discovery-section__heading"><div><p className="eyebrow">SHOP THE INTEREST</p><h2>推荐商品</h2></div><Link href="/catalog">浏览全部商品<ArrowRight aria-hidden="true" size={17} /></Link></div>
        {loading ? <div className="content-skeleton-grid">{Array.from({ length: 4 }, (_, index) => <span key={index} />)}</div> : errors.has("products") ? <EmptyState title="推荐商品暂时加载失败" actionLabel="重新加载" onAction={() => void loadHome()} /> : products.length === 0 ? <EmptyState title="暂时没有在售商品" action={{ href: "/community", label: "先逛社区" }} /> : <div className="product-grid home-product-grid">{products.map((product) => <ProductCard key={product.id} product={product} />)}</div>}
      </section>

      <section className="discovery-section">
        <div className="discovery-section__heading"><div><p className="eyebrow">ONE ITEM · ONE STOCK</p><h2>个人闲置</h2></div><Link href="/catalog?sellerType=2">查看全部闲置<ArrowRight aria-hidden="true" size={17} /></Link></div>
        {loading ? <div className="content-skeleton-grid">{Array.from({ length: 4 }, (_, index) => <span key={index} />)}</div> : errors.has("personal") ? <EmptyState title="个人闲置暂时加载失败" actionLabel="重新加载" onAction={() => void loadHome()} /> : personalProducts.length === 0 ? <EmptyState title="暂时没有新的个人闲置" description="个人卖家发布并通过审核后，会在这里展示。" action={{ href: "/personal/listings/new", label: "发布一件闲置" }} /> : <div className="product-grid home-product-grid">{personalProducts.map((product) => <ProductCard key={product.id} product={product} />)}</div>}
      </section>

      <section className="discovery-section">
        <div className="discovery-section__heading"><div><p className="eyebrow">SPECIAL COMMERCE</p><h2>限时与稀缺</h2></div></div>
        <div className="home-event-grid">
          {errors.has("releases") ? <button className="home-event home-event--retry" type="button" onClick={() => void loadHome()}><span><Timer aria-hidden="true" size={18} />限时发售</span><h3>活动加载失败</h3><p>其他区域不受影响。</p><strong>重新加载</strong></button> : releases.length ? releases.map((campaign) => <Link className="home-event home-event--release" href="/releases" key={campaign.id}><span><Timer aria-hidden="true" size={18} />限时发售</span><h3>{campaign.name}</h3><p>{formatDateTime(campaign.startTime)} 开始 · 每人限购 {campaign.perUserLimit} 件</p><strong>{campaign.items[0] ? formatMoney(campaign.items[0].campaignPrice) : "查看活动"}</strong></Link>) : <Link className="home-event home-event--release" href="/releases"><span><Timer aria-hidden="true" size={18} />限时发售</span><h3>等待下一场限时活动</h3><p>活动开放后，可选择具体 SKU 和收货地址参与购买。</p><strong>查看活动日历</strong></Link>}
          {errors.has("auctions") ? <button className="home-event home-event--retry" type="button" onClick={() => void loadHome()}><span><Gavel aria-hidden="true" size={18} />公开拍卖</span><h3>拍卖加载失败</h3><p>其他区域不受影响。</p><strong>重新加载</strong></button> : auctions.length ? auctions.map((auction) => <Link className="home-event home-event--auction" href="/auctions" key={auction.id}><span><Gavel aria-hidden="true" size={18} />公开拍卖</span><h3>{auction.name}</h3><p>{formatDateTime(auction.endTime)} 结束 · 最低加价 {formatMoney(auction.minIncrement)}</p><strong>{auction.currentAmount == null ? `起拍 ${formatMoney(auction.startingPrice)}` : `当前 ${formatMoney(auction.currentAmount)}`}</strong></Link>) : <Link className="home-event home-event--auction" href="/auctions"><span><Gavel aria-hidden="true" size={18} />公开拍卖</span><h3>等待下一件稀缺商品</h3><p>拍卖发布后，可查看商品详情、竞价记录和当前领先状态。</p><strong>进入拍卖场</strong></Link>}
        </div>
      </section>
    </div>
  );
}
