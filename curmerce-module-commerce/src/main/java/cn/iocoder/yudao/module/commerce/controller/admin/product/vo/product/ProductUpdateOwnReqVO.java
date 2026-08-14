package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductUpdateOwnReqVO extends ProductBaseSaveReqVO {
    @NotNull
    private Long id;
}
