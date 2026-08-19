import Link from "next/link";

export default function HomePage() {
  return (
    <section className="hero-grid">
      <div className="hero-card">
        <p className="eyebrow">CURMERCE · 用户商城端</p>
        <h1>从兴趣发现，到可信交易。</h1>
        <p className="hero-card__description">
          这是 Curmerce 用户端的第一段前端基础能力。目前先接通用户认证和收货地址，随后会接入分类、商品、购物车与订单闭环。
        </p>
        <div className="hero-card__actions">
          <Link className="button button--primary" href="/register">
            创建买家账号
          </Link>
          <Link className="button button--secondary" href="/addresses">
            管理收货地址
          </Link>
        </div>
      </div>
      <aside className="status-card">
        <span className="status-dot" />
        <p className="eyebrow">当前前端进度</p>
        <h2>认证与地址基础层</h2>
        <ul className="check-list">
          <li>Curmerce `/app-api` 请求客户端</li>
          <li>Token 和租户请求头</li>
          <li>登录、注册和地址管理</li>
          <li>后续接入商品目录与购物车</li>
        </ul>
      </aside>
    </section>
  );
}
