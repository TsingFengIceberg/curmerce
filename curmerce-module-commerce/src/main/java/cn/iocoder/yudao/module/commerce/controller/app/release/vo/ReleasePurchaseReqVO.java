package cn.iocoder.yudao.module.commerce.controller.app.release.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReleasePurchaseReqVO {
    @NotNull private Long itemId;
    @NotNull @Min(1) private Integer quantity;
    @NotNull private Long addressId;
    @NotBlank private String idempotencyKey;
}
