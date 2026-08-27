import { adminApi, adminMultipartApi, appApi, appMultipartApi, jsonBody } from "@/lib/api/client";

type UploadAudience = "app" | "admin";
type UploadCapabilities = {
  directUpload: boolean;
  maxUploadBytes: number;
  allowedMimeTypes: string[];
  variants: string[];
};
type UploadTicket = {
  ticketKey: string;
  uploadUrl: string;
  requiredHeaders: Record<string, string>;
  assetUrl: string;
  expiresAt: string | number;
};

const capabilityCache = new Map<UploadAudience, Promise<UploadCapabilities>>();

function api<T>(audience: UploadAudience, path: string, init?: RequestInit) {
  return audience === "admin" ? adminApi<T>(path, init) : appApi<T>(path, init);
}

function mediaPath(audience: UploadAudience, suffix: string) {
  return audience === "admin" ? `/infra/file/media/${suffix}` : `/infra/file/${suffix}`;
}

function capabilities(audience: UploadAudience) {
  let request = capabilityCache.get(audience);
  if (!request) {
    request = api<UploadCapabilities>(audience, mediaPath(audience, "upload-capabilities"))
      .catch((cause) => {
        capabilityCache.delete(audience);
        throw cause;
      });
    capabilityCache.set(audience, request);
  }
  return request;
}

async function directUpload(file: File, directory: string, audience: UploadAudience) {
  const ticket = await api<UploadTicket>(audience, mediaPath(audience, "upload-ticket"), {
    method: "POST",
    body: jsonBody({ name: file.name, directory, contentType: file.type, size: file.size, visibility: 0 }),
  });
  const response = await fetch(ticket.uploadUrl, {
    method: "PUT",
    body: file,
    headers: ticket.requiredHeaders,
  });
  if (!response.ok) throw new Error(`对象存储上传失败（HTTP ${response.status}）`);
  return api<string>(audience, mediaPath(audience, `upload-ticket/${ticket.ticketKey}/finalize`), { method: "POST" });
}

async function multipartUpload(file: File, directory: string, audience: UploadAudience) {
  const form = new FormData();
  form.append("file", file);
  form.append("directory", directory);
  return audience === "admin"
    ? adminMultipartApi<string>("/infra/file/media/upload", form)
    : appMultipartApi<string>("/infra/file/upload", form);
}

export const uploadApi = {
  async image(file: File, directory: string, audience: UploadAudience = "app") {
    const current = await capabilities(audience);
    if (file.size > current.maxUploadBytes) throw new Error("图片超过服务端允许的大小");
    if (!current.allowedMimeTypes.includes(file.type)) throw new Error("图片格式不受支持");
    return current.directUpload
      ? directUpload(file, directory, audience)
      : multipartUpload(file, directory, audience);
  },
  clearCapabilitiesCache() {
    capabilityCache.clear();
  },
};
