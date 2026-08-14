package cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantRejectReqVO {
    @NotNull private Long id;
    @NotBlank @Size(min = 2, max = 255) private String reason;
}
