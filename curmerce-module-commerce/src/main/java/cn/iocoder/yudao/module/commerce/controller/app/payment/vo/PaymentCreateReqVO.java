package cn.iocoder.yudao.module.commerce.controller.app.payment.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentCreateReqVO {
    @NotNull
    private Long orderId;
    @NotBlank
    @Size(max = 32)
    private String paymentMethod;
}
