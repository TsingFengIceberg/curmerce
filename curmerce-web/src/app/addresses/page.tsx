"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { MapPin, Pencil, Plus, Star, Trash2 } from "lucide-react";
import { CurmerceApiError } from "@/lib/api/client";
import { memberApi } from "@/lib/api/member";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import type { AreaNode, MemberAddress, MemberAddressInput } from "@/lib/types/api";
import { Notice } from "@/components/notice";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Drawer } from "@/components/drawer";
import { EmptyState } from "@/components/empty-state";

function createEmptyForm(): MemberAddressInput {
  return { name: "", mobile: "", areaId: 0, detailAddress: "", defaultStatus: false };
}

export default function AddressesPage() {
  const router = useRouter();
  const [addresses, setAddresses] = useState<MemberAddress[]>([]);
  const [form, setForm] = useState<MemberAddressInput>(createEmptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [areaTree, setAreaTree] = useState<AreaNode[]>([]);
  const [areaLoading, setAreaLoading] = useState(true);
  const [provinceId, setProvinceId] = useState(0);
  const [cityId, setCityId] = useState(0);
  const [defaultingId, setDefaultingId] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [deletingAddress, setDeletingAddress] = useState<MemberAddress | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }
    void Promise.all([loadAddresses(), loadAreaTree()]);
  }, [router]);

  async function loadAreaTree() {
    setAreaLoading(true);
    try {
      setAreaTree(await memberApi.areaTree());
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "地区数据加载失败");
    } finally {
      setAreaLoading(false);
    }
  }

  async function loadAddresses() {
    setLoading(true);
    setError(null);
    try {
      setAddresses(await memberApi.listAddresses());
    } catch (cause) {
      if (cause instanceof CurmerceApiError && cause.status === 401) {
        clearToken();
        router.replace("/login");
        return;
      }
      setError(cause instanceof CurmerceApiError ? cause.message : "地址加载失败");
    } finally {
      setLoading(false);
    }
  }

  function update(field: keyof MemberAddressInput, value: string | boolean) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function findAreaPath(nodes: AreaNode[], targetId: number, path: AreaNode[] = []): AreaNode[] | null {
    for (const node of nodes) {
      const nextPath = [...path, node];
      if (node.id === targetId) return nextPath;
      const found = findAreaPath(node.children ?? [], targetId, nextPath);
      if (found) return found;
    }
    return null;
  }

  function startEdit(address: MemberAddress) {
    setEditingId(address.id);
    const path = findAreaPath(areaTree, address.areaId) ?? [];
    setProvinceId(path[0]?.id ?? 0);
    setCityId(path[1]?.id ?? 0);
    setForm({
      name: address.name,
      mobile: address.mobile,
      areaId: address.areaId,
      detailAddress: address.detailAddress,
      defaultStatus: address.defaultStatus,
    });
    setMessage(null);
    setError(null);
    setEditorOpen(true);
  }

  function resetForm() {
    setEditingId(null);
    setProvinceId(0);
    setCityId(0);
    setForm(createEmptyForm());
  }

  function openCreate() {
    resetForm();
    setMessage(null);
    setError(null);
    setEditorOpen(true);
  }

  function closeEditor() {
    if (saving) return;
    setEditorOpen(false);
    resetForm();
  }

  const provinceNodes = areaTree;
  const cityNodes = provinceNodes.find((node) => node.id === provinceId)?.children ?? [];
  const districtNodes = cityNodes.find((node) => node.id === cityId)?.children ?? [];

  function selectProvince(value: string) {
    const nextProvinceId = Number(value);
    const province = provinceNodes.find((node) => node.id === nextProvinceId);
    setProvinceId(nextProvinceId);
    setCityId(0);
    setForm((current) => ({ ...current, areaId: province && (province.children?.length ?? 0) === 0 ? province.id : 0 }));
  }

  function selectCity(value: string) {
    const nextCityId = Number(value);
    const city = cityNodes.find((node) => node.id === nextCityId);
    setCityId(nextCityId);
    setForm((current) => ({ ...current, areaId: city && (city.children?.length ?? 0) === 0 ? city.id : 0 }));
  }

  function selectDistrict(value: string) {
    setForm((current) => ({ ...current, areaId: Number(value) }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      if (!form.areaId || form.areaId < 1) throw new Error("请选择有效的收货地区");
      if (editingId) {
        await memberApi.updateAddress({ ...form, id: editingId });
        setMessage("地址已更新");
      } else {
        await memberApi.createAddress(form);
        setMessage("地址已添加");
      }
      resetForm();
      setEditorOpen(false);
      await loadAddresses();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError || cause instanceof Error ? cause.message : "地址保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!deletingAddress) return;
    setDeleting(true);
    setError(null);
    try {
      await memberApi.deleteAddress(deletingAddress.id);
      setMessage("地址已删除");
      if (editingId === deletingAddress.id) closeEditor();
      setAddresses((current) => current.filter((item) => item.id !== deletingAddress.id));
      setDeletingAddress(null);
      await loadAddresses();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "地址删除失败");
    } finally {
      setDeleting(false);
    }
  }

  async function makeDefault(address: MemberAddress) {
    if (address.defaultStatus) return;
    setDefaultingId(address.id);
    setMessage(null);
    setError(null);
    try {
      await memberApi.updateAddress({
        id: address.id,
        name: address.name,
        mobile: address.mobile,
        areaId: address.areaId,
        detailAddress: address.detailAddress,
        defaultStatus: true,
      });
      setMessage("默认地址已切换");
      await loadAddresses();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "默认地址切换失败");
    } finally {
      setDefaultingId(null);
    }
  }

  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">ACCOUNT · DELIVERY</p>
          <h1>收货地址</h1>
          <p>维护买家下单时使用的地址快照。</p>
        </div>
        <button className="button button--primary button--icon-label" type="button" onClick={openCreate}><Plus aria-hidden="true" size={17} />新建地址</button>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="address-list address-list--productized">
          <div className="panel-heading">
            <h2>我的地址</h2>
            <span>{addresses.length} 条</span>
          </div>
          {loading ? <p className="empty-state">加载中…</p> : null}
          {!loading && addresses.length === 0 ? <EmptyState icon={<MapPin aria-hidden="true" size={22} />} title="还没有收货地址" description="添加常用地址后，下单时可以直接选择。" actionLabel="添加第一条地址" onAction={openCreate} /> : null}
          <div className="address-cards">
            {addresses.map((address) => (
              <article className={`address-card address-card--interactive${address.defaultStatus ? " address-card--default" : ""}`} key={address.id}>
                <div className="address-card__topline">
                  <div>
                    <strong>{address.name}</strong>
                    <span>{address.mobile}</span>
                  </div>
                  {address.defaultStatus ? <span className="tag"><Star aria-hidden="true" size={12} />默认地址</span> : null}
                </div>
                <p>{address.areaName ? `${address.areaName} · ` : ""}{address.detailAddress}</p>
                <div className="address-card__actions">
                  {!address.defaultStatus ? <button className="text-button button--icon-label" disabled={defaultingId === address.id} type="button" onClick={() => void makeDefault(address)}><Star aria-hidden="true" size={14} />{defaultingId === address.id ? "切换中…" : "设为默认"}</button> : <span className="address-card__default-hint">当前默认地址</span>}
                  <button aria-label={`编辑 ${address.name} 的地址`} className="icon-button" title="编辑地址" type="button" onClick={() => startEdit(address)}><Pencil aria-hidden="true" size={16} /></button>
                  <button aria-label={`删除 ${address.name} 的地址`} className="icon-button icon-button--danger" title="删除地址" type="button" onClick={() => setDeletingAddress(address)}><Trash2 aria-hidden="true" size={16} /></button>
                </div>
              </article>
            ))}
          </div>
      </div>
      <Drawer open={editorOpen} title={editingId ? "编辑收货地址" : "新建收货地址"} description="该地址会用于订单收货信息快照。" busy={saving} onClose={closeEditor}>
        <form className="drawer-form" onSubmit={submit}>
          <label className="field"><span>收件人</span><input required maxLength={30} value={form.name} onChange={(event) => update("name", event.target.value)} placeholder="姓名" /></label>
          <label className="field"><span>手机号</span><input required inputMode="tel" value={form.mobile} onChange={(event) => update("mobile", event.target.value)} placeholder="手机号" /></label>
          <fieldset className="field-group"><legend>收货地区</legend><div className="form-grid form-grid--three"><select aria-label="省" required disabled={areaLoading || provinceNodes.length === 0} value={provinceId || ""} onChange={(event) => selectProvince(event.target.value)}><option value="">{areaLoading ? "地区加载中…" : "选择省"}</option>{provinceNodes.map((node) => <option key={node.id} value={node.id}>{node.name}</option>)}</select><select aria-label="市" required disabled={!provinceId || cityNodes.length === 0} value={cityId || ""} onChange={(event) => selectCity(event.target.value)}><option value="">{provinceId && cityNodes.length === 0 ? "无需选择市" : "选择市"}</option>{cityNodes.map((node) => <option key={node.id} value={node.id}>{node.name}</option>)}</select><select aria-label="区" required disabled={!cityId || districtNodes.length === 0} value={form.areaId || ""} onChange={(event) => selectDistrict(event.target.value)}><option value="">{cityId && districtNodes.length === 0 ? "无需选择区" : "选择区"}</option>{districtNodes.map((node) => <option key={node.id} value={node.id}>{node.name}</option>)}</select></div></fieldset>
          <p className="field-help">普通省市请选择到区县；香港、澳门等地区树叶子节点选中后可直接保存。</p>
          <label className="field"><span>详细地址</span><textarea required maxLength={255} rows={4} value={form.detailAddress} onChange={(event) => update("detailAddress", event.target.value)} placeholder="街道、楼栋、门牌号" /></label>
          <label className="checkbox-field"><input checked={form.defaultStatus} type="checkbox" onChange={(event) => update("defaultStatus", event.target.checked)} /><span>设为默认地址</span></label>
          <div className="drawer-form__actions"><button className="button button--secondary" disabled={saving} type="button" onClick={closeEditor}>取消</button><button className="button button--primary" disabled={saving} type="submit">{saving ? "保存中…" : editingId ? "保存修改" : "添加地址"}</button></div>
        </form>
      </Drawer>
      <ConfirmDialog open={Boolean(deletingAddress)} title="删除收货地址" description={`确定删除“${deletingAddress?.name ?? ""}”的收货地址吗？删除后无法恢复。`} confirmLabel="删除地址" dangerous busy={deleting} onClose={() => { if (!deleting) setDeletingAddress(null); }} onConfirm={() => void remove()} />
    </section>
  );
}
