"use client";

import { Camera, ImageUp, RotateCcw, Trash2, X } from "lucide-react";
import { ChangeEvent, useEffect, useId, useRef, useState } from "react";
import { assetUrl, CurmerceApiError } from "@/lib/api/client";
import { uploadApi } from "@/lib/api/upload";

const MAX_FILE_BYTES = 5 * 1024 * 1024;
const OUTPUT_SIZE = 512;

type AvatarUploaderProps = {
  value?: string | null;
  name?: string;
  disabled?: boolean;
  onChange: (url: string) => void;
  onError: (message: string) => void;
};

export function AvatarUploader({ value, name = "用户", disabled = false, onChange, onError }: AvatarUploaderProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const dialogTitleId = useId();
  const imageRef = useRef<HTMLImageElement>(null);
  const [source, setSource] = useState<string | null>(null);
  const [fileName, setFileName] = useState("avatar.jpg");
  const [zoom, setZoom] = useState(1);
  const [panX, setPanX] = useState(0);
  const [panY, setPanY] = useState(0);
  const [uploading, setUploading] = useState(false);

  useEffect(() => () => {
    if (source) URL.revokeObjectURL(source);
  }, [source]);

  function close() {
    if (uploading) return;
    dialogRef.current?.close();
    if (source) URL.revokeObjectURL(source);
    setSource(null);
    setZoom(1);
    setPanX(0);
    setPanY(0);
  }

  function selectFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      onError("请选择图片文件");
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      onError("头像图片不能超过 5 MB");
      return;
    }
    if (source) URL.revokeObjectURL(source);
    setSource(URL.createObjectURL(file));
    setFileName(file.name.replace(/\.[^.]+$/, "") || "avatar");
    setZoom(1);
    setPanX(0);
    setPanY(0);
    dialogRef.current?.showModal();
  }

  async function cropAndUpload() {
    const image = imageRef.current;
    if (!image || !image.naturalWidth || !image.naturalHeight) return;
    setUploading(true);
    try {
      const cropSize = Math.min(image.naturalWidth, image.naturalHeight) / zoom;
      const sourceX = (image.naturalWidth - cropSize) * ((100 - panX) / 200);
      const sourceY = (image.naturalHeight - cropSize) * ((100 - panY) / 200);
      const canvas = document.createElement("canvas");
      canvas.width = OUTPUT_SIZE;
      canvas.height = OUTPUT_SIZE;
      const context = canvas.getContext("2d");
      if (!context) throw new Error("当前浏览器无法处理头像图片");
      context.drawImage(image, sourceX, sourceY, cropSize, cropSize, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.9));
      if (!blob) throw new Error("头像裁剪失败，请重新选择图片");
      const url = await uploadApi.image(new File([blob], `${fileName}.jpg`, { type: "image/jpeg" }), "member/avatar");
      onChange(url);
      close();
    } catch (cause) {
      onError(cause instanceof CurmerceApiError || cause instanceof Error ? cause.message : "头像上传失败");
    } finally {
      setUploading(false);
    }
  }

  const previewUrl = assetUrl(value);

  return (
    <div className="avatar-uploader">
      <div className="avatar-uploader__preview">
        {previewUrl ? <img alt={`${name}的头像`} src={previewUrl} /> : <Camera aria-hidden="true" size={30} />}
      </div>
      <div className="avatar-uploader__controls">
        <strong>头像</strong>
        <p>支持 JPG、PNG 和 WebP，最大 5 MB。保存前可裁剪为正方形。</p>
        <div className="inline-actions">
          <label className="button button--secondary button--icon-label">
            <ImageUp aria-hidden="true" size={17} />
            {previewUrl ? "更换头像" : "上传头像"}
            <input accept="image/jpeg,image/png,image/webp" disabled={disabled} hidden type="file" onChange={selectFile} />
          </label>
          {value ? (
            <button className="text-button text-button--danger button--icon-label" disabled={disabled} type="button" onClick={() => onChange("")}>
              <Trash2 aria-hidden="true" size={15} />移除
            </button>
          ) : null}
        </div>
      </div>
      <dialog aria-labelledby={dialogTitleId} className="avatar-crop-dialog" ref={dialogRef} onCancel={(event) => { event.preventDefault(); close(); }}>
        <button aria-label="关闭" className="confirm-dialog__close" disabled={uploading} title="关闭" type="button" onClick={close}><X aria-hidden="true" size={19} /></button>
        <h2 id={dialogTitleId}>裁剪头像</h2>
        <p>调整画面位置和缩放，头像会保存为清晰的正方形图片。</p>
        <div className="avatar-crop-stage">
          {source ? <img ref={imageRef} alt="头像裁剪预览" src={source} style={{ objectPosition: `${50 - panX / 2}% ${50 - panY / 2}%`, transform: `scale(${zoom})` }} /> : null}
        </div>
        <div className="avatar-crop-controls">
          <label><span>缩放</span><input aria-label="头像缩放" max="3" min="1" step="0.05" type="range" value={zoom} onChange={(event) => setZoom(Number(event.target.value))} /></label>
          <label><span>左右</span><input aria-label="头像水平位置" max="100" min="-100" step="1" type="range" value={panX} onChange={(event) => setPanX(Number(event.target.value))} /></label>
          <label><span>上下</span><input aria-label="头像垂直位置" max="100" min="-100" step="1" type="range" value={panY} onChange={(event) => setPanY(Number(event.target.value))} /></label>
        </div>
        <div className="confirm-dialog__actions">
          <button className="button button--secondary button--icon-label" disabled={uploading} type="button" onClick={() => { setZoom(1); setPanX(0); setPanY(0); }}><RotateCcw aria-hidden="true" size={16} />重置</button>
          <button className="button button--primary" disabled={uploading} type="button" onClick={() => void cropAndUpload()}>{uploading ? "上传中…" : "应用并上传"}</button>
        </div>
      </dialog>
    </div>
  );
}
