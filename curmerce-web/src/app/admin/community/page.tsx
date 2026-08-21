"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Notice } from "@/components/notice";
import { adminCommunityApi } from "@/lib/api/community";
import { CurmerceApiError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";
import type { CommunityPost, CommunityReport } from "@/lib/types/api";

export default function AdminCommunityPage() {
  const [posts, setPosts] = useState<CommunityPost[]>([]); const [reports, setReports] = useState<CommunityReport[]>([]); const [error, setError] = useState<string | null>(null); const [message, setMessage] = useState<string | null>(null);
  async function load() { try { const [p, r] = await Promise.all([adminCommunityApi.posts({ pageNo: 1, pageSize: 50 }), adminCommunityApi.reports({ pageNo: 1, pageSize: 50, status: 0 })]); setPosts(p.list ?? []); setReports(r.list ?? []); } catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "社区审核数据加载失败"); } }
  useEffect(() => { void load(); }, []);
  async function hide(id: number) { try { await adminCommunityApi.postStatus({ id, status: 2 }); setMessage("帖子已隐藏"); await load(); } catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "帖子处理失败"); } }
  async function review(id: number, status: number) { try { await adminCommunityApi.reviewReport({ id, status, remark: status === 1 ? "举报成立，已隐藏帖子" : "举报不成立" }); setMessage("举报已处理"); await load(); } catch (cause) { setError(cause instanceof CurmerceApiError ? cause.message : "举报处理失败"); } }
  return <section className="content-section admin-page community-admin-page"><div className="section-heading"><div><p className="eyebrow">ADMIN · COMMUNITY</p><h1>社区内容审核</h1><p>处理帖子状态和用户举报，审核结果会直接影响公开内容流。</p></div><Link className="button button--secondary" href="/admin/product-review">商品审核</Link></div>{message ? <Notice tone="success">{message}</Notice> : null}{error ? <Notice>{error}</Notice> : null}<div className="admin-split-layout"><div className="orders-panel"><div className="panel-heading"><h2>帖子</h2><span>{posts.length} 条</span></div>{posts.map((post) => <div className="admin-record-card" key={post.id}><div className="admin-record-card__top"><strong>{post.title}</strong><span className="tag">状态 {post.status}</span></div><p>{post.content}</p><div className="admin-record-card__meta"><span>{post.authorNickname || post.authorUserId}</span><span>{formatDateTime(post.createTime)}</span></div>{post.status !== 2 ? <button className="button button--danger" type="button" onClick={() => void hide(post.id)}>隐藏帖子</button> : null}</div>)}</div><div className="orders-panel"><div className="panel-heading"><h2>待处理举报</h2><span>{reports.length} 条</span></div>{reports.length === 0 ? <p className="empty-state">暂无待处理举报。</p> : reports.map((report) => <div className="admin-record-card" key={report.id}><div className="admin-record-card__top"><strong>帖子 #{report.postId}</strong><span>{formatDateTime(report.createTime)}</span></div><p>{report.reason}</p><div className="inline-actions"><button className="button button--danger" type="button" onClick={() => void review(report.id, 1)}>举报成立并隐藏</button><button className="button button--secondary" type="button" onClick={() => void review(report.id, 2)}>驳回举报</button></div></div>)}</div></div></section>;
}
