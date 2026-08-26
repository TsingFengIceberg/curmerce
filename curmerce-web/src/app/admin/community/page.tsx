"use client";

import { Eye, FileWarning, ImageIcon, MessageSquareWarning, Search } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { MediaImage } from "@/components/media-image";
import { Pagination } from "@/components/pagination";
import { adminCommunityApi } from "@/lib/api/community";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime } from "@/lib/format";
import type { CommunityPost, CommunityReport } from "@/lib/types/api";

const PAGE_SIZE = 12;
const postStatusLabels: Record<number, string> = { 0: "草稿", 1: "已发布", 2: "已隐藏" };
const reportStatusLabels: Record<number, string> = { 0: "待处理", 1: "举报成立", 2: "已驳回" };
type PendingAction = { kind: "post"; post: CommunityPost; targetStatus: 1 | 2 } | { kind: "report"; report: CommunityReport; targetStatus: 1 | 2 };

export default function AdminCommunityPage() {
  const router = useRouter();
  const [view, setView] = useState<"reports" | "posts">("reports");
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [reports, setReports] = useState<CommunityReport[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState(view === "reports" ? "0" : "");
  const [postDetail, setPostDetail] = useState<CommunityPost | null>(null);
  const [reportDetail, setReportDetail] = useState<CommunityReport | null>(null);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!getAdminAccessToken()) { router.replace("/merchant/login"); return; }
    void load();
  }, [router, view, pageNo, keyword, status]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      if (view === "posts") {
        const page = await adminCommunityApi.posts({ pageNo, pageSize: PAGE_SIZE, keyword, status: status ? Number(status) : undefined });
        setPosts(page.list ?? []);
        setTotal(page.total ?? 0);
      } else {
        const page = await adminCommunityApi.reports({ pageNo, pageSize: PAGE_SIZE, status: status ? Number(status) : undefined });
        setReports(page.list ?? []);
        setTotal(page.total ?? 0);
      }
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) { clearAdminToken(); router.replace("/merchant/login"); return; }
      setError(cause instanceof CurmerceApiError ? cause.message : "社区治理数据加载失败");
    } finally {
      setLoading(false);
    }
  }

  function changeView(next: "reports" | "posts") {
    setView(next);
    setPageNo(1);
    setStatus(next === "reports" ? "0" : "");
    setKeyword("");
    setKeywordInput("");
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNo(1);
    setKeyword(keywordInput.trim());
  }

  function requestPostAction(post: CommunityPost, targetStatus: 1 | 2) {
    setPostDetail(null);
    setPending({ kind: "post", post, targetStatus });
  }

  function requestReportAction(report: CommunityReport, targetStatus: 1 | 2) {
    setReportDetail(null);
    setPending({ kind: "report", report, targetStatus });
  }

  async function executeAction() {
    if (!pending) return;
    setBusy(true);
    setError(null);
    try {
      if (pending.kind === "post") {
        await adminCommunityApi.postStatus({ id: pending.post.id, status: pending.targetStatus });
        setMessage(pending.targetStatus === 2 ? "帖子已隐藏" : "帖子已恢复公开");
      } else {
        await adminCommunityApi.reviewReport({ id: pending.report.id, status: pending.targetStatus, remark: pending.targetStatus === 1 ? "举报成立，帖子已隐藏" : "经审核，举报不成立" });
        setMessage(pending.targetStatus === 1 ? "举报已确认成立，帖子已隐藏" : "举报已驳回");
      }
      setPending(null);
      await load();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "社区治理操作失败");
    } finally {
      setBusy(false);
    }
  }

  const pendingDangerous = pending?.kind === "post" ? pending.targetStatus === 2 : pending?.targetStatus === 1;
  const pendingTitle = pending?.kind === "post" ? pending.targetStatus === 2 ? "隐藏社区帖子" : "恢复公开帖子" : pending?.targetStatus === 1 ? "确认举报成立" : "驳回社区举报";
  const pendingDescription = pending?.kind === "post" ? pending.targetStatus === 2 ? `“${pending.post.title}”将从公开内容流中隐藏。` : `“${pending.post.title}”将重新出现在公开内容流中。` : pending?.targetStatus === 1 ? `确认针对“${pending.report.postTitle || `帖子 ${pending.report.postId}`}”的举报成立，并同步隐藏帖子。` : `确认该举报不成立，帖子状态不会被改变。`;

  return (
    <section className="content-section admin-page community-admin-page">
      <div className="section-heading"><div><p className="eyebrow">ADMIN · COMMUNITY</p><h1>社区治理</h1><p>结合帖子内容和举报上下文，维护公开内容流秩序。</p></div></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="admin-view-tabs" role="tablist" aria-label="社区治理视图"><button aria-selected={view === "reports"} className={view === "reports" ? "admin-view-tab admin-view-tab--active" : "admin-view-tab"} role="tab" type="button" onClick={() => changeView("reports")}><MessageSquareWarning aria-hidden="true" size={16} />举报队列</button><button aria-selected={view === "posts"} className={view === "posts" ? "admin-view-tab admin-view-tab--active" : "admin-view-tab"} role="tab" type="button" onClick={() => changeView("posts")}><FileWarning aria-hidden="true" size={16} />帖子管理</button></div>
      <div className="workspace-section admin-community-panel">
        <div className="admin-data-toolbar"><select aria-label={view === "reports" ? "举报状态" : "帖子状态"} value={status} onChange={(event) => { setStatus(event.target.value); setPageNo(1); }}><option value="">全部状态</option>{Object.entries(view === "reports" ? reportStatusLabels : postStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>{view === "posts" ? <form className="order-search" onSubmit={search}><Search aria-hidden="true" size={16} /><input aria-label="帖子内容" placeholder="搜索标题或正文" value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} /><button type="submit">查询</button></form> : <span className="admin-toolbar-spacer" />}<span>共 {total} 条</span></div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && view === "reports" && !reports.length ? <EmptyState icon={<MessageSquareWarning aria-hidden="true" size={23} />} title="当前没有待处理举报" description="可切换状态查看历史审核记录。" /> : null}
        {!loading && view === "posts" && !posts.length ? <EmptyState icon={<FileWarning aria-hidden="true" size={23} />} title="没有符合条件的帖子" description="调整关键词或状态后重新查询。" /> : null}
        {!loading && view === "reports" && reports.length ? <div className="community-moderation-table community-moderation-table--reports"><div className="community-moderation-table__head"><span>被举报帖子</span><span>举报信息</span><span>作者</span><span>提交时间</span><span>状态</span><span>操作</span></div>{reports.map((report) => <article className="community-moderation-table__row" key={report.id}><div className="moderation-post-summary"><strong>{report.postTitle || `帖子 ${report.postId}`}</strong><small>{report.postContent || "帖子内容已不可用"}</small></div><div className="admin-table-stack"><strong>{report.reason}</strong><small>举报人：{report.reporterNickname || `用户 ${report.reporterUserId}`}</small></div><div className="admin-table-stack"><strong>{report.postAuthorNickname || "未知作者"}</strong><small>{report.postAuthorUserId ? `用户 ${report.postAuthorUserId}` : "作者信息不可用"}</small></div><span className="admin-table-time">{formatDateTime(report.createTime)}</span><span className={`tag report-status report-status--${report.status}`}>{reportStatusLabels[report.status] ?? report.status}</span><div className="listing-table__actions"><button aria-label={`查看举报 ${report.id}`} className="icon-button" title="查看完整上下文" type="button" onClick={() => setReportDetail(report)}><Eye aria-hidden="true" size={16} /></button>{report.status === 0 ? <button className="text-button text-button--danger" type="button" onClick={() => requestReportAction(report, 1)}>成立</button> : null}{report.status === 0 ? <button className="text-button" type="button" onClick={() => requestReportAction(report, 2)}>驳回</button> : null}</div></article>)}</div> : null}
        {!loading && view === "posts" && posts.length ? <div className="community-moderation-table community-moderation-table--posts"><div className="community-moderation-table__head"><span>帖子</span><span>作者</span><span>互动</span><span>发布时间</span><span>状态</span><span>操作</span></div>{posts.map((post) => <article className="community-moderation-table__row" key={post.id}><div className="moderation-post-summary"><strong>{post.title}</strong><small>{post.content}</small></div><div className="admin-table-stack"><strong>{post.authorNickname || `用户 ${post.authorUserId}`}</strong><small>{post.topics.map((topic) => `#${topic.name}`).join(" ") || "无话题"}</small></div><div className="admin-table-stack"><strong>{post.likeCount} 赞 · {post.favoriteCount} 收藏</strong><small>{post.commentCount} 条评论</small></div><span className="admin-table-time">{formatDateTime(post.createTime)}</span><span className={`tag post-status post-status--${post.status}`}>{postStatusLabels[post.status] ?? post.status}</span><div className="listing-table__actions"><button aria-label={`查看 ${post.title}`} className="icon-button" title="查看帖子详情" type="button" onClick={() => setPostDetail(post)}><Eye aria-hidden="true" size={16} /></button>{post.status === 2 ? <button className="text-button" type="button" onClick={() => requestPostAction(post, 1)}>恢复</button> : <button className="text-button text-button--danger" type="button" onClick={() => requestPostAction(post, 2)}>隐藏</button>}</div></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>

      <Drawer open={Boolean(reportDetail)} title="举报审核上下文" description={reportDetail ? `举报记录 ${reportDetail.id}` : ""} onClose={() => setReportDetail(null)}>{reportDetail ? <div className="drawer-form"><div className="moderation-report-reason"><span>举报原因</span><strong>{reportDetail.reason}</strong><small>{reportDetail.reporterNickname || `用户 ${reportDetail.reporterUserId}`} · {formatDateTime(reportDetail.createTime)}</small></div><ModerationPost title={reportDetail.postTitle || `帖子 ${reportDetail.postId}`} content={reportDetail.postContent || "帖子内容已不可用"} images={reportDetail.postMediaUrls ?? []} author={reportDetail.postAuthorNickname || (reportDetail.postAuthorUserId ? `用户 ${reportDetail.postAuthorUserId}` : "未知作者")} />{reportDetail.reviewRemark ? <div className="detail-rows"><div><span>审核结论</span><strong>{reportStatusLabels[reportDetail.status]}</strong></div><div><span>审核备注</span><strong>{reportDetail.reviewRemark}</strong></div><div><span>审核时间</span><strong>{formatDateTime(reportDetail.reviewTime)}</strong></div></div> : null}{reportDetail.status === 0 ? <div className="drawer-form__actions"><button className="button button--secondary" type="button" onClick={() => requestReportAction(reportDetail, 2)}>驳回举报</button><button className="button button--danger" type="button" onClick={() => requestReportAction(reportDetail, 1)}>举报成立并隐藏</button></div> : null}</div> : null}</Drawer>
      <Drawer open={Boolean(postDetail)} title="帖子治理详情" description={postDetail ? `帖子 ${postDetail.id}` : ""} onClose={() => setPostDetail(null)}>{postDetail ? <div className="drawer-form"><ModerationPost title={postDetail.title} content={postDetail.content} images={postDetail.mediaUrls ?? []} author={postDetail.authorNickname || `用户 ${postDetail.authorUserId}`} /><div className="detail-rows"><div><span>内容状态</span><strong>{postStatusLabels[postDetail.status] ?? postDetail.status}</strong></div><div><span>互动数据</span><strong>{postDetail.likeCount} 赞 · {postDetail.favoriteCount} 收藏 · {postDetail.commentCount} 评论</strong></div><div><span>发布时间</span><strong>{formatDateTime(postDetail.createTime)}</strong></div></div><div className="drawer-form__actions">{postDetail.status === 2 ? <button className="button button--primary" type="button" onClick={() => requestPostAction(postDetail, 1)}>恢复公开</button> : <button className="button button--danger" type="button" onClick={() => requestPostAction(postDetail, 2)}>隐藏帖子</button>}</div></div> : null}</Drawer>
      <ConfirmDialog open={Boolean(pending)} title={pendingTitle} description={pendingDescription} confirmLabel="确认处理" dangerous={pendingDangerous} busy={busy} onClose={() => setPending(null)} onConfirm={() => void executeAction()} />
    </section>
  );
}

function ModerationPost({ title, content, images, author }: { title: string; content: string; images: string[]; author: string }) {
  return <article className="moderation-post"><div><span className="moderation-post__author">{author}</span><h3>{title}</h3><p>{content}</p></div>{images.length ? <div className="moderation-post__images">{images.map((url) => <MediaImage alt={`${title} 配图`} fallback={<span className="moderation-post__no-image"><ImageIcon aria-hidden="true" size={18} />图片加载失败</span>} key={url} src={assetUrl(url)} />)}</div> : <div className="moderation-post__no-image"><ImageIcon aria-hidden="true" size={18} />该帖子没有图片</div>}</article>;
}
