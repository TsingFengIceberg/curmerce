package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductIdReqVO {
    @NotNull
    private Long id;
}
