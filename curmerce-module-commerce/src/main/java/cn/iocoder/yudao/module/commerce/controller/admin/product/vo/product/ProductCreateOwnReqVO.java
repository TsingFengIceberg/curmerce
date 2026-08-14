package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductCreateOwnReqVO extends ProductBaseSaveReqVO {
    @NotBlank @Size(min = 2, max = 64)
    private String code;
}
