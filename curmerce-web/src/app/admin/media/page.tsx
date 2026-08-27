"use client";

import { ImageOff, Images, RefreshCcw, Search, ShieldCheck, ShieldX } from "lucide-react";
import { FormEvent, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { EmptyState } from "@/components/empty-state";
import { Notice } from "@/components/notice";
import { Pagination } from "@/components/pagination";
import { adminMediaApi, type MediaAsset } from "@/lib/api/admin-media";
import { CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import { formatDateTime } from "@/lib/format";

const PAGE_SIZE = 12;
const assetStatusLabels: Record<number, string> = { 0: "处理中", 10: "可用", 20: "已隔离", 30: "失败" };
const scanStatusLabels: Record<number, string> = { 0: "待扫描", 10: "扫描通过", 20: "病毒拒绝", 30: "扫描跳过" };
const moderationStatusLabels: Record<number, string> = { 0: "待审核", 10: "安全", 20: "需复核", 30: "已拒绝", 40: "审核异常", 50: "已跳过" };
type MediaAction = "quarantine" | "release" | "reject" | "retry";
type PendingAction = { asset: MediaAsset; action: MediaAction };

export default function AdminMediaPage() {
  const router = useRouter();
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [assetKeyInput, setAssetKeyInput] = useState("");
  const [assetKey, setAssetKey] = useState("");
  const [assetStatus, setAssetStatus] = useState("");
  const [moderationStatus, setModerationStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loadSequence = useRef(0);

  useEffect(() => {
    if (!getAdminAccessToken()) { router.replace("/merchant/login"); return; }
    void load();
  }, [router, pageNo, assetKey, assetStatus, moderationStatus]);

  async function load() {
    const sequence = ++loadSequence.current;
    setLoading(true);
    setError(null);
    try {
      const page = await adminMediaApi.page({
        pageNo,
        pageSize: PAGE_SIZE,
        assetKey: assetKey || undefined,
        assetStatus: assetStatus ? Number(assetStatus) : undefined,
        moderationStatus: moderationStatus ? Number(moderationStatus) : undefined,
      });
      if (sequence !== loadSequence.current) return;
      setAssets(page.list ?? []);
      setTotal(page.total ?? 0);
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) { clearAdminToken(); router.replace("/merchant/login"); return; }
      if (sequence === loadSequence.current) {
        setError(cause instanceof CurmerceApiError ? cause.message : "媒体资产加载失败");
      }
    } finally {
      if (sequence === loadSequence.current) setLoading(false);
    }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNo(1);
    setAssetKey(assetKeyInput.trim());
  }

  async function executeAction() {
    if (!pending) return;
    setBusy(true);
    setError(null);
    try {
      const { asset, action } = pending;
      if (action === "quarantine") await adminMediaApi.quarantine(asset.id, "管理员人工隔离");
      if (action === "release") await adminMediaApi.release(asset.id, "管理员复核通过");
      if (action === "reject") await adminMediaApi.reject(asset.id, "管理员确认拒绝");
      if (action === "retry") await adminMediaApi.retry(asset.id);
      setMessage({ quarantine: "资产已隔离", release: "资产已解除隔离", reject: "资产已拒绝", retry: "已重新提交处理" }[action]);
      setPending(null);
      await load();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "媒体治理操作失败");
    } finally {
      setBusy(false);
    }
  }

  const actionMeta = pending ? {
    quarantine: { title: "隔离媒体资产", description: "该资产及其衍生版本将立即停止对外提供。", label: "确认隔离", dangerous: true },
    release: { title: "解除媒体隔离", description: "该资产会恢复访问，并重新生成缺失的衍生版本。", label: "确认放行", dangerous: false },
    reject: { title: "拒绝媒体资产", description: "该资产会保持隔离并记录管理员拒绝结论。", label: "确认拒绝", dangerous: true },
    retry: { title: "重新处理媒体资产", description: "系统将再次执行内容审核和衍生图生成。", label: "重新处理", dangerous: false },
  }[pending.action] : { title: "", description: "", label: "确认", dangerous: false };

  return (
    <section className="content-section admin-page media-admin-page">
      <div className="section-heading"><div><p className="eyebrow">ADMIN · MEDIA</p><h1>媒体治理</h1><p>查看图片安全状态、内容审核结果与存储生命周期。</p></div></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="workspace-section media-admin-panel">
        <div className="admin-data-toolbar media-admin-toolbar">
          <select aria-label="资产状态" value={assetStatus} onChange={(event) => { setAssetStatus(event.target.value); setPageNo(1); }}><option value="">全部资产状态</option>{Object.entries(assetStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
          <select aria-label="审核状态" value={moderationStatus} onChange={(event) => { setModerationStatus(event.target.value); setPageNo(1); }}><option value="">全部审核状态</option>{Object.entries(moderationStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
          <form className="order-search" onSubmit={search}><Search aria-hidden="true" size={16} /><input aria-label="资产编号" placeholder="输入完整资产编号" value={assetKeyInput} onChange={(event) => setAssetKeyInput(event.target.value)} /><button type="submit">查询</button></form>
          <span>共 {total} 条</span>
        </div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !assets.length ? <EmptyState icon={<Images aria-hidden="true" size={24} />} title="没有符合条件的媒体资产" description="调整资产状态、审核状态或资产编号后重新查询。" /> : null}
        {!loading && assets.length ? <div className="media-admin-table"><div className="media-admin-table__head"><span>预览与文件</span><span>上传者</span><span>安全状态</span><span>生命周期</span><span>创建时间</span><span>操作</span></div>{assets.map((asset) => <article className="media-admin-table__row" key={asset.id}><div className="media-asset-identity"><AdminMediaThumbnail asset={asset} /><div><strong>{asset.name || asset.assetKey}</strong><small>{asset.width && asset.height ? `${asset.width} × ${asset.height} · ` : ""}{formatBytes(asset.size)}</small><code title={asset.assetKey}>{asset.assetKey}</code></div></div><div className="admin-table-stack media-asset-owner"><strong>{asset.ownerUserType === 2 ? "后台用户" : asset.ownerUserType === 1 ? "普通用户" : "历史资产"}</strong><small>{asset.ownerUserId ? `用户 ${asset.ownerUserId}` : "无上传者记录"} · {asset.visibility === 10 ? "私有" : "公开"}</small></div><div className="admin-table-stack media-asset-safety"><span className={`tag media-status media-status--${asset.assetStatus}`}>{assetStatusLabels[asset.assetStatus] ?? asset.assetStatus}</span><small>{scanStatusLabels[asset.scanStatus] ?? asset.scanStatus} · {moderationStatusLabels[asset.moderationStatus] ?? asset.moderationStatus}</small>{asset.moderationReason ? <small title={asset.moderationReason}>{asset.moderationReason}</small> : null}{asset.failureReason ? <small className="media-failure" title={asset.failureReason}>{asset.failureReason}</small> : null}</div><div className="admin-table-stack media-asset-lifecycle"><strong>{asset.boundOnce ? "已绑定业务" : "从未绑定"}</strong><small>{asset.orphanedAt ? `孤立于 ${formatDateTime(asset.orphanedAt)}` : "正在被引用"}</small></div><span className="admin-table-time media-asset-created">{formatDateTime(asset.createTime)}</span><div className="listing-table__actions media-asset-actions">{asset.assetStatus === 10 || asset.assetStatus === 0 ? <button className="text-button text-button--danger" type="button" onClick={() => setPending({ asset, action: "quarantine" })}><ShieldX aria-hidden="true" size={14} />隔离</button> : null}{asset.assetStatus === 20 ? <button className="text-button" type="button" onClick={() => setPending({ asset, action: "release" })}><ShieldCheck aria-hidden="true" size={14} />放行</button> : null}<button className="icon-button" title="重新处理" aria-label={`重新处理 ${asset.name || asset.assetKey}`} type="button" onClick={() => setPending({ asset, action: "retry" })}><RefreshCcw aria-hidden="true" size={15} /></button>{asset.moderationStatus !== 30 && asset.assetStatus !== 30 ? <button className="text-button text-button--danger" type="button" onClick={() => setPending({ asset, action: "reject" })}>拒绝</button> : null}</div></article>)}</div> : null}
        <Pagination pageNo={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo} />
      </div>
      <ConfirmDialog open={Boolean(pending)} title={actionMeta.title} description={pending ? `${actionMeta.description} 文件：${pending.asset.name || pending.asset.assetKey}` : actionMeta.description} confirmLabel={actionMeta.label} dangerous={actionMeta.dangerous} busy={busy} onClose={() => { if (!busy) setPending(null); }} onConfirm={() => void executeAction()} />
    </section>
  );
}

function AdminMediaThumbnail({ asset }: { asset: MediaAsset }) {
  const [source, setSource] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    void adminMediaApi.preview(asset.id).then((blob) => {
      if (!active) return;
      objectUrl = URL.createObjectURL(blob);
      setSource(objectUrl);
    }).catch(() => { if (active) setSource(null); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [asset.id, asset.assetStatus]);
  return source ? <img alt="" src={source} /> : <span><ImageOff aria-hidden="true" size={18} /></span>;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
