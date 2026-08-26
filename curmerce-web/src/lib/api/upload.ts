import { adminMultipartApi, appMultipartApi } from "@/lib/api/client";

export const uploadApi = {
  image(file: File, directory: string, audience: "app" | "admin" = "app") {
    const form = new FormData();
    form.append("file", file);
    form.append("directory", directory);
    return audience === "admin"
      ? adminMultipartApi<string>("/infra/file/upload", form)
      : appMultipartApi<string>("/infra/file/upload", form);
  },
};
