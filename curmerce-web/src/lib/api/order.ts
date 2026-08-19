import { appApi, jsonBody } from "@/lib/api/client";
import type { OrderCreateResult } from "@/lib/types/api";

export const orderApi = {
  create(addressId: number, idempotencyKey: string) {
    return appApi<OrderCreateResult>("/commerce/order/create", {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey,
      },
      body: jsonBody({ addressId }),
    });
  },
};
