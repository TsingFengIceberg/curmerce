"use client";

import { ArrowLeft, ArrowRight, Crop, Eye, ImagePlus, RefreshCw, RotateCcw, Star, Trash2, X } from "lucide-react";
import { ChangeEvent, useEffect, useRef, useState } from "react";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { uploadApi } from "@/lib/api/upload";

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const MAX_IMAGE_EDGE = 1920;
const CROP_OUTPUT_SIZE = 1200;

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
  const cropDialogRef = useRef<HTMLDialogElement>(null);
  const cropImageRef = useRef<HTMLImageElement>(null);
  const [cropSource, setCropSource] = useState<string | null>(null);
  const [cropIndex, setCropIndex] = useState<number | null>(null);
  const [cropZoom, setCropZoom] = useState(1);
  const [cropX, setCropX] = useState(0);
  const [cropY, setCropY] = useState(0);
  const [compressionSaved, setCompressionSaved] = useState(0);

  useEffect(() => () => { if (cropSource) URL.revokeObjectURL(cropSource); }, [cropSource]);

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
        const prepared = await compressImage(file);
        setCompressionSaved((current) => current + Math.max(0, file.size - prepared.size));
        uploaded.push(await uploadApi.image(prepared, directory, audience));
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

  async function openCrop(url: string, index: number) {
    try {
      const response = await fetch(assetUrl(url) ?? url);
      if (!response.ok) throw new Error("图片读取失败");
      if (cropSource) URL.revokeObjectURL(cropSource);
      setCropSource(URL.createObjectURL(await response.blob()));
      setCropIndex(index);
      setCropZoom(1);
      setCropX(0);
      setCropY(0);
      cropDialogRef.current?.showModal();
    } catch (cause) {
      report(cause instanceof Error ? cause.message : "当前图片无法裁切");
    }
  }

  function closeCrop() {
    if (uploading) return;
    cropDialogRef.current?.close();
    if (cropSource) URL.revokeObjectURL(cropSource);
    setCropSource(null);
    setCropIndex(null);
  }

  async function applyCrop() {
    const image = cropImageRef.current;
    if (!image || cropIndex == null || !image.naturalWidth || !image.naturalHeight) return;
    setUploading({ current: 1, total: 1 });
    try {
      const cropSize = Math.min(image.naturalWidth, image.naturalHeight) / cropZoom;
      const sourceX = (image.naturalWidth - cropSize) * ((100 - cropX) / 200);
      const sourceY = (image.naturalHeight - cropSize) * ((100 - cropY) / 200);
      const canvas = document.createElement("canvas");
      canvas.width = CROP_OUTPUT_SIZE;
      canvas.height = CROP_OUTPUT_SIZE;
      const context = canvas.getContext("2d");
      if (!context) throw new Error("当前浏览器无法裁切图片");
      context.drawImage(image, sourceX, sourceY, cropSize, cropSize, 0, 0, CROP_OUTPUT_SIZE, CROP_OUTPUT_SIZE);
      const blob = await canvasBlob(canvas, "image/webp", 0.86);
      const url = await uploadApi.image(new File([blob], `cropped-${Date.now()}.webp`, { type: blob.type }), directory, audience);
      onChange(value.map((item, index) => index === cropIndex ? url : item));
      closeCrop();
    } catch (cause) {
      report(cause instanceof Error ? cause.message : "图片裁切上传失败");
    } finally {
      setUploading(null);
    }
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
                <button aria-label="裁切图片" title="裁切为正方形" type="button" onClick={() => void openCrop(url, index)}><Crop aria-hidden="true" size={15} /></button>
                <button aria-label="前移" disabled={index === 0} title="前移" type="button" onClick={() => move(index, index - 1)}><ArrowLeft aria-hidden="true" size={15} /></button>
                <button aria-label="后移" disabled={index === value.length - 1} title="后移" type="button" onClick={() => move(index, index + 1)}><ArrowRight aria-hidden="true" size={15} /></button>
                <button aria-label="移除图片" title="移除图片" type="button" onClick={() => onChange(value.filter((_, itemIndex) => itemIndex !== index))}><Trash2 aria-hidden="true" size={15} /></button>
              </div>
            </article>
          ))}
        </div>
      ) : <div className="image-uploader__empty"><ImagePlus aria-hidden="true" size={24} /><span>{label === "商品图片" ? "至少上传一张商品图片" : "暂未添加图片"}</span></div>}
      {failures.length ? <div className="upload-failures">{failures.map((item) => <div key={item.id}><span><strong>{item.file.name}</strong><small>{item.message}</small></span><button className="text-button button--icon-label" disabled={Boolean(uploading)} type="button" onClick={() => void retry(item)}><RefreshCw aria-hidden="true" size={14} />重试</button></div>)}</div> : null}
      <small className="field-help">已上传 {value.length}/{maxCount} 张，每张不超过 10 MB。浏览器会自动将长边压缩到 {MAX_IMAGE_EDGE}px。{compressionSaved ? ` 本次已减少 ${formatBytes(compressionSaved)}。` : ""}</small>
      <dialog aria-label="图片预览" className="image-preview-dialog" ref={previewRef} onCancel={(event) => { event.preventDefault(); previewRef.current?.close(); }}>
        <button aria-label="关闭预览" className="confirm-dialog__close" title="关闭" type="button" onClick={() => previewRef.current?.close()}><X aria-hidden="true" size={20} /></button>
        {previewUrl ? <img alt="图片大图预览" src={assetUrl(previewUrl) ?? ""} /> : null}
      </dialog>
      <dialog aria-label="图片裁切" className="avatar-crop-dialog" ref={cropDialogRef} onCancel={(event) => { event.preventDefault(); closeCrop(); }}>
        <button aria-label="关闭裁切" className="confirm-dialog__close" disabled={Boolean(uploading)} title="关闭" type="button" onClick={closeCrop}><X aria-hidden="true" size={19} /></button>
        <h2>裁切图片</h2><p>调整缩放和位置，应用后将生成 1200 × 1200 的 WebP 图片并替换当前图片。</p>
        <div className="avatar-crop-stage">{cropSource ? <img ref={cropImageRef} alt="图片裁切预览" src={cropSource} style={{ objectPosition: `${50 - cropX / 2}% ${50 - cropY / 2}%`, transform: `scale(${cropZoom})` }} /> : null}</div>
        <div className="avatar-crop-controls"><label><span>缩放</span><input aria-label="图片缩放" max="3" min="1" step="0.05" type="range" value={cropZoom} onChange={(event) => setCropZoom(Number(event.target.value))} /></label><label><span>左右</span><input aria-label="图片水平位置" max="100" min="-100" type="range" value={cropX} onChange={(event) => setCropX(Number(event.target.value))} /></label><label><span>上下</span><input aria-label="图片垂直位置" max="100" min="-100" type="range" value={cropY} onChange={(event) => setCropY(Number(event.target.value))} /></label></div>
        <div className="confirm-dialog__actions"><button className="button button--secondary button--icon-label" disabled={Boolean(uploading)} type="button" onClick={() => { setCropZoom(1); setCropX(0); setCropY(0); }}><RotateCcw aria-hidden="true" size={16} />重置</button><button className="button button--primary" disabled={Boolean(uploading)} type="button" onClick={() => void applyCrop()}>{uploading ? "处理中…" : "应用裁切"}</button></div>
      </dialog>
    </div>
  );
}

async function compressImage(file: File) {
  const source = URL.createObjectURL(file);
  try {
    const image = await loadImage(source);
    const scale = Math.min(1, MAX_IMAGE_EDGE / Math.max(image.naturalWidth, image.naturalHeight));
    if (scale === 1 && file.type === "image/webp" && file.size < 1_500_000) return file;
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
    const context = canvas.getContext("2d");
    if (!context) return file;
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    const blob = await canvasBlob(canvas, "image/webp", 0.84);
    if (blob.size >= file.size && scale === 1) return file;
    return new File([blob], `${file.name.replace(/\.[^.]+$/, "") || "image"}.webp`, { type: blob.type, lastModified: file.lastModified });
  } finally {
    URL.revokeObjectURL(source);
  }
}

function loadImage(source: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("图片解码失败"));
    image.src = source;
  });
}

function canvasBlob(canvas: HTMLCanvasElement, type: string, quality: number) {
  return new Promise<Blob>((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error("图片处理失败")), type, quality));
}

function formatBytes(bytes: number) {
  return bytes >= 1_048_576 ? `${(bytes / 1_048_576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}
