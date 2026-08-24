"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Notice } from "@/components/notice";
import { CurmerceApiError } from "@/lib/api/client";
import { adminCategoryApi } from "@/lib/api/admin-product";
import { clearAdminToken, getAdminAccessToken } from "@/lib/auth/storage";
import type { ProductCategoryNode } from "@/lib/types/api";

interface CategoryForm {
  id?: number;
  parentId: string;
  code: string;
  name: string;
  imageUrl: string;
  sort: string;
}

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
  const [form, setForm] = useState<CategoryForm>(emptyForm);
  const [editing, setEditing] = useState<ProductCategoryNode | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const flatCategories = useMemo(() => flatten(categories), [categories]);

  useEffect(() => {
    if (!getAdminAccessToken()) {
      router.replace("/merchant/login");
      return;
    }
    void loadCategories();
  }, [router]);

  async function loadCategories() {
    setLoading(true);
    setError(null);
    try {
      setCategories((await adminCategoryApi.tree()) ?? []);
    } catch (cause) {
      handleError(cause, "商品分类加载失败");
    } finally {
      setLoading(false);
    }
  }

  function handleError(cause: unknown, fallback: string) {
    if (cause instanceof CurmerceApiError && cause.status === 401) {
      clearAdminToken();
      router.replace("/merchant/login");
      return;
    }
    setError(cause instanceof CurmerceApiError ? cause.message : fallback);
  }

  function startCreate() {
    setEditing(null);
    setForm(emptyForm);
    setError(null);
  }

  function startEdit(node: ProductCategoryNode) {
    setEditing(node);
    setForm({
      id: node.id,
      parentId: node.parentId ? String(node.parentId) : "",
      code: node.code,
      name: node.name,
      imageUrl: node.imageUrl ?? "",
      sort: String(node.sort ?? 0),
    });
    setError(null);
  }

  function updateField<K extends keyof CategoryForm>(key: K, value: CategoryForm[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function saveCategory(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.name.trim() || !form.sort.trim()) {
      setError("请填写分类名称和排序值");
      return;
    }
    if (!editing && form.code.trim().length < 2) {
      setError("新分类编码至少需要 2 个字符");
      return;
    }
    if (editing && form.parentId && collectDescendantIds(editing).includes(Number(form.parentId))) {
      setError("父分类不能选择当前分类或其子孙分类");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const parentId = form.parentId ? Number(form.parentId) : undefined;
      if (editing) {
        await adminCategoryApi.update({ id: editing.id, parentId, name: form.name.trim(), imageUrl: form.imageUrl.trim(), sort: Number(form.sort) });
        setMessage("商品分类已更新");
      } else {
        await adminCategoryApi.create({ parentId, code: form.code.trim(), name: form.name.trim(), imageUrl: form.imageUrl.trim(), sort: Number(form.sort) });
        setMessage("商品分类已创建");
      }
      await loadCategories();
      startCreate();
    } catch (cause) {
      handleError(cause, "保存商品分类失败");
    } finally {
      setBusy(false);
    }
  }

  async function toggleStatus(node: ProductCategoryNode) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await adminCategoryApi.updateStatus({ id: node.id, status: node.status === 1 ? 0 : 1 });
      setMessage(node.status === 0 ? "分类已停用" : "分类已启用");
      await loadCategories();
    } catch (cause) {
      handleError(cause, "更新分类状态失败");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    clearAdminToken();
    router.replace("/merchant/login");
  }

  return (
    <section className="content-section admin-page category-admin-page">
      <div className="section-heading">
        <div><p className="eyebrow">ADMIN · CATALOG</p><h1>平台商品分类</h1><p>维护商品分类树，并在后端校验父子关系与循环引用。</p></div>
        <div className="inline-actions"><Link className="button button--secondary" href="/admin/product-review">商品审核</Link><Link className="button button--secondary" href="/admin/refunds">退款审核</Link><button className="button button--secondary" type="button" onClick={() => void logout()}>退出后台</button></div>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="admin-split-layout category-admin-layout">
        <div className="orders-panel">
          <div className="panel-heading"><h2>分类树</h2><button className="text-button" type="button" onClick={startCreate}>新建分类</button></div>
          {loading ? <p className="empty-state">分类加载中…</p> : null}
          {!loading && categories.length === 0 ? <p className="empty-state">还没有商品分类。</p> : null}
          <div className="category-admin-tree">
            {flatCategories.map(({ node, depth }) => (
              <div className={`category-admin-row${editing?.id === node.id ? " category-admin-row--active" : ""}`} key={node.id} style={{ paddingLeft: `${16 + depth * 22}px` }}>
                <div className="category-admin-row__name"><span className="category-admin-row__branch">{depth > 0 ? "↳" : "●"}</span><div><strong>{node.name}</strong><small>{node.code} · 排序 {node.sort}</small></div></div>
                <div className="inline-actions"><span className={`tag category-status category-status--${node.status}`}>{node.status === 0 ? "启用" : "停用"}</span><button className="text-button" type="button" onClick={() => startEdit(node)}>编辑</button><button className="text-button" disabled={busy} type="button" onClick={() => void toggleStatus(node)}>{node.status === 0 ? "停用" : "启用"}</button></div>
              </div>
            ))}
          </div>
        </div>
        <form className="orders-panel admin-form-panel" onSubmit={saveCategory}>
          <div className="panel-heading"><h2>{editing ? "编辑分类" : "创建分类"}</h2>{editing ? <button className="text-button" type="button" onClick={startCreate}>取消编辑</button> : null}</div>
          {!editing ? <label className="field"><span>分类编码</span><input maxLength={32} onChange={(event) => updateField("code", event.target.value)} placeholder="例如 hobby" value={form.code} /></label> : <div className="form-readonly">编码：{editing.code}</div>}
          <label className="field"><span>父分类</span><select onChange={(event) => updateField("parentId", event.target.value)} value={form.parentId}><option value="">顶级分类</option>{flatCategories.filter(({ node }) => !editing || !collectDescendantIds(editing).includes(node.id)).map(({ node, depth }) => <option key={node.id} value={node.id}>{"　".repeat(depth)}{node.name}</option>)}</select></label>
          <label className="field"><span>分类名称</span><input maxLength={64} onChange={(event) => updateField("name", event.target.value)} value={form.name} /></label>
          <label className="field"><span>图片地址（可选）</span><input maxLength={1024} onChange={(event) => updateField("imageUrl", event.target.value)} value={form.imageUrl} /></label>
          <label className="field"><span>排序</span><input min="0" onChange={(event) => updateField("sort", event.target.value)} type="number" value={form.sort} /></label>
          <button className="button button--primary button--full" disabled={busy} type="submit">{busy ? "保存中…" : editing ? "保存修改" : "创建分类"}</button>
        </form>
      </div>
    </section>
  );
}
