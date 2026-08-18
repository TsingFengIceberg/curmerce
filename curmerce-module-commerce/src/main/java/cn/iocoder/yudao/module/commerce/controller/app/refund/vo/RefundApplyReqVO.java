package cn.iocoder.yudao.module.commerce.controller.app.refund.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundApplyReqVO {
    @NotNull(message = "订单不能为空")
    private Long orderId;

    @NotBlank(message = "退款原因不能为空")
    @Size(max = 255, message = "退款原因不能超过 255 个字符")
    private String reason;
}
