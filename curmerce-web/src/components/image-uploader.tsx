"use client";

import { ArrowLeft, ArrowRight, Eye, ImagePlus, RefreshCw, Star, Trash2, X } from "lucide-react";
import { ChangeEvent, useRef, useState } from "react";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { uploadApi } from "@/lib/api/upload";

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

type FailedUpload = { id: string; file: File; message: string };

type ImageUploaderProps = {
  value: string[];
  onChange: (urls: string[]) => void;
  directory: string;
  audience?: "app" | "admin";
  maxCount?: number;
  disabled?: boolean;
  onError?: (message: string) => void;
  label?: string;
  description?: string;
};

export function ImageUploader({ value, onChange, directory, audience = "app", maxCount = 9, disabled = false, onError, label = "商品图片", description = "第一张作为封面，可拖动或使用箭头调整顺序。建议使用清晰的 1:1 图片。" }: ImageUploaderProps) {
  const previewRef = useRef<HTMLDialogElement>(null);
  const draggedIndex = useRef<number | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState<{ current: number; total: number } | null>(null);
  const [failures, setFailures] = useState<FailedUpload[]>([]);

  function report(message: string) {
    onError?.(message);
  }

  async function uploadFiles(files: File[]) {
    const remaining = Math.max(0, maxCount - value.length);
    const selected = files.slice(0, remaining);
    if (files.length > remaining) report(`最多上传 ${maxCount} 张图片，已保留前 ${remaining} 张`);
    const valid = selected.filter((file) => {
      if (!file.type.startsWith("image/")) {
        report(`${file.name} 不是可用的图片文件`);
        return false;
      }
      if (file.size > MAX_IMAGE_BYTES) {
        report(`${file.name} 超过 10 MB`);
        return false;
      }
      return true;
    });
    const uploaded: string[] = [];
    const failed: FailedUpload[] = [];
    for (let index = 0; index < valid.length; index += 1) {
      const file = valid[index];
      setUploading({ current: index + 1, total: valid.length });
      try {
        uploaded.push(await uploadApi.image(file, directory, audience));
      } catch (cause) {
        failed.push({ id: `${file.name}-${file.lastModified}-${index}`, file, message: cause instanceof CurmerceApiError || cause instanceof Error ? cause.message : "上传失败" });
      }
    }
    if (uploaded.length) onChange([...value, ...uploaded]);
    if (failed.length) {
      setFailures((current) => [...current, ...failed]);
      report(`${failed.length} 张图片上传失败，可在下方重试`);
    }
    setUploading(null);
  }

  function chooseFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    event.target.value = "";
    if (files.length) void uploadFiles(files);
  }

  async function retry(item: FailedUpload) {
    setFailures((current) => current.filter((failure) => failure.id !== item.id));
    await uploadFiles([item.file]);
  }

  function move(from: number, to: number) {
    if (to < 0 || to >= value.length || from === to) return;
    const next = [...value];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    onChange(next);
  }

  function showPreview(url: string) {
    setPreviewUrl(url);
    previewRef.current?.showModal();
  }

  return (
    <div className="image-uploader">
      <div className="image-uploader__toolbar">
        <div><strong>{label}</strong><span>{description}</span></div>
        <label className="button button--secondary button--small button--icon-label">
          <ImagePlus aria-hidden="true" size={16} />选择图片
          <input accept="image/jpeg,image/png,image/webp" disabled={disabled || Boolean(uploading) || value.length >= maxCount} hidden multiple type="file" onChange={chooseFiles} />
        </label>
      </div>
      {uploading ? <div aria-live="polite" className="upload-progress"><span style={{ width: `${Math.round((uploading.current / uploading.total) * 100)}%` }} /><small>正在上传 {uploading.current}/{uploading.total}</small></div> : null}
      {value.length ? (
        <div className="image-uploader__grid">
          {value.map((url, index) => (
            <article
              className="image-uploader__item"
              draggable={!disabled}
              key={`${url}-${index}`}
              onDragStart={() => { draggedIndex.current = index; }}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => { if (draggedIndex.current !== null) move(draggedIndex.current, index); draggedIndex.current = null; }}
            >
              <img alt={index === 0 ? "商品封面" : `商品图片 ${index + 1}`} src={assetUrl(url) ?? ""} />
              {index === 0 ? <span className="image-uploader__cover"><Star aria-hidden="true" size={11} />封面</span> : null}
              <div className="image-uploader__actions">
                <button aria-label="预览大图" title="预览大图" type="button" onClick={() => showPreview(url)}><Eye aria-hidden="true" size={15} /></button>
                <button aria-label="前移" disabled={index === 0} title="前移" type="button" onClick={() => move(index, index - 1)}><ArrowLeft aria-hidden="true" size={15} /></button>
                <button aria-label="后移" disabled={index === value.length - 1} title="后移" type="button" onClick={() => move(index, index + 1)}><ArrowRight aria-hidden="true" size={15} /></button>
                <button aria-label="移除图片" title="移除图片" type="button" onClick={() => onChange(value.filter((_, itemIndex) => itemIndex !== index))}><Trash2 aria-hidden="true" size={15} /></button>
              </div>
            </article>
          ))}
        </div>
      ) : <div className="image-uploader__empty"><ImagePlus aria-hidden="true" size={24} /><span>{label === "商品图片" ? "至少上传一张商品图片" : "暂未添加图片"}</span></div>}
      {failures.length ? <div className="upload-failures">{failures.map((item) => <div key={item.id}><span><strong>{item.file.name}</strong><small>{item.message}</small></span><button className="text-button button--icon-label" disabled={Boolean(uploading)} type="button" onClick={() => void retry(item)}><RefreshCw aria-hidden="true" size={14} />重试</button></div>)}</div> : null}
      <small className="field-help">已上传 {value.length}/{maxCount} 张，每张不超过 10 MB。</small>
      <dialog aria-label="图片预览" className="image-preview-dialog" ref={previewRef} onCancel={(event) => { event.preventDefault(); previewRef.current?.close(); }}>
        <button aria-label="关闭预览" className="confirm-dialog__close" title="关闭" type="button" onClick={() => previewRef.current?.close()}><X aria-hidden="true" size={20} /></button>
        {previewUrl ? <img alt="图片大图预览" src={assetUrl(previewUrl) ?? ""} /> : null}
      </dialog>
    </div>
  );
}
