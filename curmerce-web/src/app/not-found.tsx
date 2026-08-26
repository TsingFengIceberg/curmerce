import Link from "next/link";
import { ArrowLeft, Compass } from "lucide-react";

export default function NotFound() {
  return <section className="route-error"><span><Compass aria-hidden="true" size={26} /></span><p className="eyebrow">404 · NOT FOUND</p><h1>没有找到这个页面</h1><p>链接可能已经失效，或者相关内容已下架。你可以回到发现页继续浏览。</p><div><Link className="button button--primary button--icon-label" href="/community"><Compass aria-hidden="true" size={16} />去发现</Link><Link className="button button--secondary button--icon-label" href="/"><ArrowLeft aria-hidden="true" size={16} />返回首页</Link></div></section>;
}
