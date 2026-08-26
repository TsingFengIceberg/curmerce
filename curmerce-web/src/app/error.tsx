"use client";

import Link from "next/link";
import { AlertCircle, RotateCcw } from "lucide-react";
import { useEffect } from "react";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { console.error(error); }, [error]);
  return <section className="route-error" role="alert"><span><AlertCircle aria-hidden="true" size={26} /></span><p className="eyebrow">RECOVERABLE ERROR</p><h1>这个页面暂时没有完成加载</h1><p>你的账号状态和已填写内容不会因此被清除。可以重试当前页面，或先返回首页继续浏览。</p>{error.digest ? <small>错误标识：{error.digest}</small> : null}<div><button className="button button--primary button--icon-label" type="button" onClick={reset}><RotateCcw aria-hidden="true" size={16} />重新加载</button><Link className="button button--secondary" href="/">返回首页</Link></div></section>;
}
