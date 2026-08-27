import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/client", () => ({
  appApi: vi.fn(),
  adminApi: vi.fn(),
  appMultipartApi: vi.fn(),
  adminMultipartApi: vi.fn(),
  jsonBody: JSON.stringify,
}));

import { adminApi, appApi, appMultipartApi } from "@/lib/api/client";
import { uploadApi } from "@/lib/api/upload";

afterEach(() => {
  vi.restoreAllMocks();
  vi.clearAllMocks();
  uploadApi.clearCapabilitiesCache();
});

describe("uploadApi", () => {
  it("falls back to authenticated multipart upload for database storage", async () => {
    vi.mocked(appApi).mockResolvedValueOnce({
      directUpload: false,
      maxUploadBytes: 1024,
      allowedMimeTypes: ["image/png"],
      variants: [],
    });
    vi.mocked(appMultipartApi).mockResolvedValueOnce("/app-api/infra/file/assets/database");
    const file = new File([new Uint8Array([1, 2])], "test.png", { type: "image/png" });

    await expect(uploadApi.image(file, "community")).resolves.toBe("/app-api/infra/file/assets/database");
    expect(appMultipartApi).toHaveBeenCalledOnce();
  });

  it("uses a constrained ticket, object PUT, and finalize call for S3 storage", async () => {
    vi.mocked(adminApi)
      .mockResolvedValueOnce({ directUpload: true, maxUploadBytes: 1024, allowedMimeTypes: ["image/png"], variants: [] })
      .mockResolvedValueOnce({
        ticketKey: "ticket-1",
        uploadUrl: "http://object.test/upload",
        requiredHeaders: { "Content-Type": "image/png" },
        assetUrl: "/pending",
        expiresAt: "2030-01-01T00:00:00",
      })
      .mockResolvedValueOnce("/app-api/infra/file/assets/final");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 200 })));
    const file = new File([new Uint8Array([1, 2])], "test.png", { type: "image/png" });

    await expect(uploadApi.image(file, "category", "admin")).resolves.toBe("/app-api/infra/file/assets/final");
    expect(fetch).toHaveBeenCalledWith("http://object.test/upload", expect.objectContaining({ method: "PUT", body: file }));
    expect(adminApi).toHaveBeenLastCalledWith("/infra/file/media/upload-ticket/ticket-1/finalize", { method: "POST" });
  });
});
