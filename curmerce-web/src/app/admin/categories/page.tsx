"use client";

import { ChevronDown, ChevronRight, GripVertical, ImageIcon, Pencil, Plus, Tags } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";
import { ImageUploader } from "@/components/image-uploader";
import { Notice } from "@/components/notice";
import { adminCategoryApi } from "@/lib/api/admin-product";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import type { ProductCategoryNode } from "@/lib/types/api";

type CategoryForm = { id?: number; parentId: string; code: string; name: string; imageUrl: string; sort: string };
const emptyForm: CategoryForm = { parentId: "", code: "", name: "", imageUrl: "", sort: "0" };

function flatten(nodes: ProductCategoryNode[], depth = 0): Array<{ node: ProductCategoryNode; depth: number }> {
  return nodes.flatMap((node) => [{ node, depth }, ...flatten(node.children ?? [], depth + 1)]);
}

function collectDescendantIds(node: ProductCategoryNode): number[] {
  return [node.id, ...(node.children ?? []).flatMap(collectDescendantIds)];
}

export default function AdminCategoriesPage() {
  const router = useRouter();
  const [categories, setCategories] = useState<ProductCategoryNode[]>([]);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<ProductCategoryNode | null>(null);
  const [form, setForm] = useState<CategoryForm>(emptyForm);
  const [dragId, setDragId] = useState<number | null>(null);
  const [focusedId, setFocusedId] = useState<number | null>(null);
  const [pendingStatus, setPendingStatus] = useState<ProductCategoryNode | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const flatCategories = useMemo(() => flatten(categories), [categories]);

  useEffect(() => {
    if (!getAdminAccessToken()) { router.replace("/merchant/login"); return; }
    const focus = Number(new URLSearchParams(window.location.search).get("focus"));
    if (Number.isFinite(focus) && focus > 0) setFocusedId(focus);
    void loadCategories();
  }, [router]);

  async function loadCategories() {
    setLoading(true);
    setError(null);
    try {
      const tree = (await adminCategoryApi.tree()) ?? [];
      setCategories(tree);
      setExpanded((current) => current.size ? current : new Set(flatten(tree).filter(({ node }) => node.children?.length).map(({ node }) => node.id)));
    } catch (cause) {
      handle(cause, "商品分类加载失败");
    } finally {
      setLoading(false);
    }
  }

  function handle(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      router.replace("/merchant/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  function openCreate(parent?: ProductCategoryNode) {
    setEditing(null);
    setForm({ ...emptyForm, parentId: parent ? String(parent.id) : "", sort: String(parent?.children?.length ? Math.max(...parent.children.map((node) => node.sort)) + 10 : 0) });
    setEditorOpen(true);
    setError(null);
  }

  function openEdit(node: ProductCategoryNode) {
    setEditing(node);
    setForm({ id: node.id, parentId: node.parentId ? String(node.parentId) : "", code: node.code, name: node.name, imageUrl: node.imageUrl ?? "", sort: String(node.sort ?? 0) });
    setEditorOpen(true);
    setError(null);
  }

  async function saveCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.name.trim() || !form.sort.trim() || (!editing && form.code.trim().length < 2)) {
      setError("请填写分类名称、排序值和至少 2 个字符的分类识别码");
      return;
    }
    if (editing && form.parentId && collectDescendantIds(editing).includes(Number(form.parentId))) {
      setError("父分类不能选择当前分类或其子分类");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const parentId = form.parentId ? Number(form.parentId) : undefined;
      if (editing) await adminCategoryApi.update({ id: editing.id, parentId, name: form.name.trim(), imageUrl: form.imageUrl.trim(), sort: Number(form.sort) });
      else await adminCategoryApi.create({ parentId, code: form.code.trim(), name: form.name.trim(), imageUrl: form.imageUrl.trim(), sort: Number(form.sort) });
      setMessage(editing ? "商品分类已更新" : "商品分类已创建");
      setEditorOpen(false);
      await loadCategories();
    } catch (cause) {
      handle(cause, "保存商品分类失败");
    } finally {
      setBusy(false);
    }
  }

  async function toggleStatus() {
    if (!pendingStatus) return;
    setBusy(true);
    setError(null);
    try {
      await adminCategoryApi.updateStatus({ id: pendingStatus.id, status: pendingStatus.status === 0 ? 1 : 0 });
      setMessage(pendingStatus.status === 0 ? "分类已停用" : "分类已启用");
      setPendingStatus(null);
      await loadCategories();
    } catch (cause) {
      handle(cause, "更新分类状态失败");
    } finally {
      setBusy(false);
    }
  }

  async function reorder(targetId: number) {
    if (!dragId || dragId === targetId) return;
    const source = flatCategories.find(({ node }) => node.id === dragId)?.node;
    const target = flatCategories.find(({ node }) => node.id === targetId)?.node;
    setDragId(null);
    if (!source || !target || (source.parentId ?? null) !== (target.parentId ?? null)) {
      setError("拖动排序仅支持同一父分类下的分类；调整层级请使用编辑功能");
      return;
    }
    const siblings = (source.parentId ? flatCategories.find(({ node }) => node.id === source.parentId)?.node.children : categories) ?? [];
    const ordered = siblings.filter((node) => node.id !== source.id);
    ordered.splice(ordered.findIndex((node) => node.id === target.id), 0, source);
    setBusy(true);
    setError(null);
    try {
      await Promise.all(ordered.map((node, index) => adminCategoryApi.update({ id: node.id, parentId: node.parentId ?? undefined, name: node.name, imageUrl: node.imageUrl ?? "", sort: index * 10 })));
      setMessage("同级分类顺序已更新");
      await loadCategories();
    } catch (cause) {
      handle(cause, "分类排序失败");
    } finally {
      setBusy(false);
    }
  }

  function toggleExpanded(id: number) {
    setExpanded((current) => { const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  }

  return (
    <section className="content-section admin-page category-admin-page">
      <div className="section-heading"><div><p className="eyebrow">ADMIN · CATALOG</p><h1>商品分类</h1><p>维护分类层级、图片、展示顺序和启停状态。</p></div><button className="button button--primary button--icon-label" type="button" onClick={() => openCreate()}><Plus aria-hidden="true" size={17} />创建分类</button></div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="workspace-section category-tree-panel">
        <div className="workspace-section__heading category-tree-heading"><div><h2>分类树</h2><span>{flatCategories.length} 个分类</span></div><small><GripVertical aria-hidden="true" size={14} />拖动同级分类可调整展示顺序</small></div>
        {loading ? <div className="order-list-skeleton"><span /><span /><span /></div> : null}
        {!loading && !categories.length ? <EmptyState icon={<Tags aria-hidden="true" size={23} />} title="还没有商品分类" description="先创建顶级分类，再按业务需要添加子分类。" actionLabel="创建分类" onAction={() => openCreate()} /> : null}
        {!loading ? <div className="category-tree-list">{categories.map((node) => <CategoryTreeRow busy={busy} depth={0} expanded={expanded} focusedId={focusedId} key={node.id} node={node} onCreate={openCreate} onDrop={(targetId) => void reorder(targetId)} onEdit={openEdit} onStatus={setPendingStatus} onToggle={toggleExpanded} setDragId={setDragId} />)}</div> : null}
      </div>

      <Drawer open={editorOpen} title={editing ? "编辑商品分类" : form.parentId ? "创建子分类" : "创建顶级分类"} description={editing ? editing.name : form.parentId ? `父分类：${flatCategories.find(({ node }) => node.id === Number(form.parentId))?.node.name ?? "—"}` : "顶级分类将直接展示在商城分类导航中。"} busy={busy} onClose={() => setEditorOpen(false)}><form className="drawer-form" onSubmit={saveCategory}>{!editing ? <label className="field"><span>分类识别码</span><input maxLength={32} minLength={2} placeholder="例如 hobby-camera" required value={form.code} onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))} /></label> : <div className="form-readonly">分类识别码：{editing.code}</div>}<label className="field"><span>父分类</span><select value={form.parentId} onChange={(event) => setForm((current) => ({ ...current, parentId: event.target.value }))}><option value="">顶级分类</option>{flatCategories.filter(({ node }) => !editing || !collectDescendantIds(editing).includes(node.id)).map(({ node, depth }) => <option key={node.id} value={node.id}>{"　".repeat(depth)}{node.name}</option>)}</select></label><label className="field"><span>分类名称</span><input maxLength={64} required value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} /></label><div className="field"><span>分类图片</span><ImageUploader audience="admin" directory="category" maxCount={1} value={form.imageUrl ? [form.imageUrl] : []} onChange={(urls) => setForm((current) => ({ ...current, imageUrl: urls[0] ?? "" }))} /></div><label className="field"><span>排序值</span><input min="0" required type="number" value={form.sort} onChange={(event) => setForm((current) => ({ ...current, sort: event.target.value }))} /><small className="field-help">数值越小越靠前，也可保存后在分类树中直接拖动。</small></label><div className="drawer-form__actions"><button className="button button--secondary" disabled={busy} type="button" onClick={() => setEditorOpen(false)}>取消</button><button className="button button--primary" disabled={busy} type="submit">{busy ? "保存中…" : editing ? "保存修改" : "创建分类"}</button></div></form></Drawer>
      <ConfirmDialog open={Boolean(pendingStatus)} title={pendingStatus?.status === 0 ? "停用商品分类" : "启用商品分类"} description={pendingStatus?.status === 0 ? `停用“${pendingStatus?.name ?? ""}”后，新商品不能再选择该分类；已有商品不会被删除。` : `启用“${pendingStatus?.name ?? ""}”后，该分类可重新用于商品发布。`} confirmLabel={pendingStatus?.status === 0 ? "确认停用" : "确认启用"} dangerous={pendingStatus?.status === 0} busy={busy} onClose={() => setPendingStatus(null)} onConfirm={() => void toggleStatus()} />
    </section>
  );
}

function CategoryTreeRow({ node, depth, expanded, focusedId, busy, onToggle, onCreate, onEdit, onStatus, onDrop, setDragId }: { node: ProductCategoryNode; depth: number; expanded: Set<number>; focusedId: number | null; busy: boolean; onToggle: (id: number) => void; onCreate: (node: ProductCategoryNode) => void; onEdit: (node: ProductCategoryNode) => void; onStatus: (node: ProductCategoryNode) => void; onDrop: (id: number) => void; setDragId: (id: number | null) => void }) {
  const hasChildren = Boolean(node.children?.length);
  const open = expanded.has(node.id);
  return <><div className={`category-tree-row${focusedId === node.id ? " category-tree-row--focused" : ""}`} draggable={!busy} onDragStart={() => setDragId(node.id)} onDragEnd={() => setDragId(null)} onDragOver={(event) => event.preventDefault()} onDrop={() => onDrop(node.id)}><div className="category-tree-row__main" style={{ paddingLeft: `${10 + depth * 26}px` }}><button aria-label={open ? `折叠 ${node.name}` : `展开 ${node.name}`} className="category-tree-toggle" disabled={!hasChildren} type="button" onClick={() => onToggle(node.id)}>{hasChildren ? open ? <ChevronDown aria-hidden="true" size={16} /> : <ChevronRight aria-hidden="true" size={16} /> : <span />}</button><GripVertical aria-hidden="true" className="category-tree-drag" size={16} />{assetUrl(node.imageUrl) ? <img alt="" src={assetUrl(node.imageUrl) ?? ""} /> : <span className="category-tree-image"><ImageIcon aria-hidden="true" size={15} /></span>}<div><strong>{node.name}</strong><small>{node.code} · 排序 {node.sort}</small></div></div><span className={`tag category-status category-status--${node.status}`}>{node.status === 0 ? "启用" : "停用"}</span><div className="listing-table__actions"><button aria-label={`添加 ${node.name} 的子分类`} className="icon-button" title="添加子分类" type="button" onClick={() => onCreate(node)}><Plus aria-hidden="true" size={16} /></button><button aria-label={`编辑 ${node.name}`} className="icon-button" title="编辑分类" type="button" onClick={() => onEdit(node)}><Pencil aria-hidden="true" size={16} /></button><button className={node.status === 0 ? "text-button text-button--danger" : "text-button"} disabled={busy} type="button" onClick={() => onStatus(node)}>{node.status === 0 ? "停用" : "启用"}</button></div></div>{hasChildren && open ? node.children.map((child) => <CategoryTreeRow busy={busy} depth={depth + 1} expanded={expanded} focusedId={focusedId} key={child.id} node={child} onCreate={onCreate} onDrop={onDrop} onEdit={onEdit} onStatus={onStatus} onToggle={onToggle} setDragId={setDragId} />) : null}</>;
}
