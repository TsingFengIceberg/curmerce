package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductRejectReqVO {
    @NotNull
    private Long id;
    @NotBlank @Size(max = 255)
    private String reason;
}
