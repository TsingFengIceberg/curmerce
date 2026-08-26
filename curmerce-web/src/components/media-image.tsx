"use client";

import { ImageOff } from "lucide-react";
import { ImgHTMLAttributes, ReactNode, useEffect, useState } from "react";

type MediaImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, "src"> & {
  src?: string | null;
  fallback?: ReactNode;
  fallbackClassName?: string;
  fallbackLabel?: string;
};

export function MediaImage({ src, fallback, fallbackClassName = "media-image-fallback", fallbackLabel = "暂无图片", onError, ...props }: MediaImageProps) {
  const [failed, setFailed] = useState(!src);

  useEffect(() => setFailed(!src), [src]);

  if (!src || failed) {
    return fallback ?? <span aria-label={fallbackLabel} className={fallbackClassName} role="img"><ImageOff aria-hidden="true" size={22} /><span>{fallbackLabel}</span></span>;
  }

  return <img {...props} src={src} onError={(event) => { onError?.(event); setFailed(true); }} />;
}
