import Link from "next/link";

export default function HomePage() {
  return (
    <section className="hero-grid">
      <div className="hero-card">
        <p className="eyebrow">CURMERCE · 用户商城端</p>
        <h1>从兴趣发现，到可信交易。</h1>
        <p className="hero-card__description">
          Curmerce 用户端现在已经接通商品目录、商品详情、购物车和单店结算。先从兴趣发现商品，再用一个可追踪的订单完成交易。
        </p>
        <div className="hero-card__actions">
          <Link className="button button--primary" href="/catalog">
            浏览商品目录
          </Link>
          <Link className="button button--secondary" href="/cart">
            查看购物车
          </Link>
        </div>
      </div>
      <aside className="status-card">
        <span className="status-dot" />
        <p className="eyebrow">当前前端进度</p>
        <h2>商城交易第一段闭环</h2>
        <ul className="check-list">
          <li>Curmerce `/app-api` 请求客户端</li>
          <li>Token 和租户请求头</li>
          <li>分类、商品列表、详情和 SKU</li>
          <li>购物车、地址选择和幂等下单</li>
        </ul>
      </aside>
    </section>
  );
}
