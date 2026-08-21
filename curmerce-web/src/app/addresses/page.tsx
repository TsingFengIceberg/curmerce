"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CurmerceApiError } from "@/lib/api/client";
import { memberApi } from "@/lib/api/member";
import { clearToken, getAccessToken } from "@/lib/auth/storage";
import type { AreaNode, MemberAddress, MemberAddressInput } from "@/lib/types/api";
import { Notice } from "@/components/notice";

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
  }

  function resetForm() {
    setEditingId(null);
    setProvinceId(0);
    setCityId(0);
    setForm(createEmptyForm());
  }

  const provinceNodes = areaTree;
  const cityNodes = provinceNodes.find((node) => node.id === provinceId)?.children ?? [];
  const districtNodes = cityNodes.find((node) => node.id === cityId)?.children ?? [];

  function selectProvince(value: string) {
    setProvinceId(Number(value));
    setCityId(0);
    setForm((current) => ({ ...current, areaId: 0 }));
  }

  function selectCity(value: string) {
    setCityId(Number(value));
    setForm((current) => ({ ...current, areaId: 0 }));
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
      if (!form.areaId || form.areaId < 1) throw new Error("请选择完整的省、市、区");
      if (editingId) {
        await memberApi.updateAddress({ ...form, id: editingId });
        setMessage("地址已更新");
      } else {
        await memberApi.createAddress(form);
        setMessage("地址已添加");
      }
      resetForm();
      await loadAddresses();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError || cause instanceof Error ? cause.message : "地址保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function remove(address: MemberAddress) {
    if (!window.confirm(`确定删除“${address.name}”的收货地址吗？`)) return;
    setError(null);
    try {
      await memberApi.deleteAddress(address.id);
      setMessage("地址已删除");
      if (editingId === address.id) resetForm();
      setAddresses((current) => current.filter((item) => item.id !== address.id));
      await loadAddresses();
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "地址删除失败");
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
        <span className="section-heading__note">后端：/member/address/*</span>
      </div>
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
      <div className="address-layout">
        <div className="address-list">
          <div className="panel-heading">
            <h2>我的地址</h2>
            <span>{addresses.length} 条</span>
          </div>
          {loading ? <p className="empty-state">加载中…</p> : null}
          {!loading && addresses.length === 0 ? <p className="empty-state">还没有收货地址，请在右侧添加。</p> : null}
          <div className="address-cards">
            {addresses.map((address) => (
              <article className={`address-card${address.defaultStatus ? " address-card--default" : ""}`} key={address.id}>
                <div className="address-card__topline">
                  <div>
                    <strong>{address.name}</strong>
                    <span>{address.mobile}</span>
                  </div>
                  {address.defaultStatus ? <span className="tag">默认地址</span> : null}
                </div>
                <p>{address.areaName ? `${address.areaName} · ` : ""}{address.detailAddress}</p>
                <div className="address-card__actions">
                  {!address.defaultStatus ? <button className="text-button" disabled={defaultingId === address.id} type="button" onClick={() => void makeDefault(address)}>{defaultingId === address.id ? "切换中…" : "设为默认"}</button> : null}
                  <button className="text-button" type="button" onClick={() => startEdit(address)}>编辑</button>
                  <button className="text-button text-button--danger" type="button" onClick={() => void remove(address)}>删除</button>
                </div>
              </article>
            ))}
          </div>
        </div>
        <form className="form-card" onSubmit={submit}>
          <div className="form-card__heading">
            <div>
              <p className="eyebrow">ADDRESS BOOK</p>
              <h2>{editingId ? "编辑地址" : "添加地址"}</h2>
            </div>
            {editingId ? <button className="text-button" type="button" onClick={resetForm}>取消编辑</button> : null}
          </div>
          <label className="field"><span>收件人</span><input required maxLength={30} value={form.name} onChange={(event) => update("name", event.target.value)} placeholder="姓名" /></label>
          <label className="field"><span>手机号</span><input required inputMode="tel" value={form.mobile} onChange={(event) => update("mobile", event.target.value)} placeholder="手机号" /></label>
          <fieldset className="field-group"><legend>收货地区</legend><div className="form-grid form-grid--three"><select aria-label="省" required disabled={areaLoading || provinceNodes.length === 0} value={provinceId || ""} onChange={(event) => selectProvince(event.target.value)}><option value="">{areaLoading ? "地区加载中…" : "选择省"}</option>{provinceNodes.map((node) => <option key={node.id} value={node.id}>{node.name}</option>)}</select><select aria-label="市" required disabled={!provinceId || cityNodes.length === 0} value={cityId || ""} onChange={(event) => selectCity(event.target.value)}><option value="">选择市</option>{cityNodes.map((node) => <option key={node.id} value={node.id}>{node.name}</option>)}</select><select aria-label="区" required disabled={!cityId || districtNodes.length === 0} value={form.areaId || ""} onChange={(event) => selectDistrict(event.target.value)}><option value="">选择区</option>{districtNodes.map((node) => <option key={node.id} value={node.id}>{node.name}</option>)}</select></div></fieldset>
          <p className="field-help">地区编号由系统地区树提供，保存时由后端再次校验。</p>
          <label className="field"><span>详细地址</span><textarea required maxLength={255} rows={4} value={form.detailAddress} onChange={(event) => update("detailAddress", event.target.value)} placeholder="街道、楼栋、门牌号" /></label>
          <label className="checkbox-field"><input checked={form.defaultStatus} type="checkbox" onChange={(event) => update("defaultStatus", event.target.checked)} /><span>设为默认地址</span></label>
          <button className="button button--primary button--full" disabled={saving} type="submit">{saving ? "保存中…" : editingId ? "保存修改" : "添加地址"}</button>
        </form>
      </div>
    </section>
  );
}
