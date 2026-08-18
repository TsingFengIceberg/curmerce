package cn.iocoder.yudao.module.commerce.controller.admin.refund.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundCallbackReqVO {
    @NotBlank
    @Size(max = 40)
    private String refundNo;
    @NotBlank
    @Size(max = 64)
    private String callbackId;
    @NotNull
    private Boolean success;
}
