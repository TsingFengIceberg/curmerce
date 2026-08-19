import { appApi, jsonBody } from "@/lib/api/client";
import type { PaymentCallbackResult, PaymentCreateResult } from "@/lib/types/api";

export const paymentApi = {
  create(orderId: number) {
    return appApi<PaymentCreateResult>("/commerce/payment/create", {
      method: "POST",
      body: jsonBody({ orderId, paymentMethod: "SIMULATED" }),
    });
  },

  simulateCallback(input: { paymentNo: string; callbackId: string; paidAmount: number }) {
    return appApi<PaymentCallbackResult>("/commerce/payment/simulate-callback", {
      method: "POST",
      body: jsonBody(input),
    });
  },
};
