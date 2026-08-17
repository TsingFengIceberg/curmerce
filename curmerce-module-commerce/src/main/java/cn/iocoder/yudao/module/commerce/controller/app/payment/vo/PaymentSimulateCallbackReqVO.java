package cn.iocoder.yudao.module.commerce.controller.app.payment.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentSimulateCallbackReqVO {
    @NotBlank
    @Size(max = 64)
    private String paymentNo;
    @NotBlank
    @Size(max = 64)
    private String callbackId;
    @NotNull
    @Min(0)
    private Long paidAmount;
}
