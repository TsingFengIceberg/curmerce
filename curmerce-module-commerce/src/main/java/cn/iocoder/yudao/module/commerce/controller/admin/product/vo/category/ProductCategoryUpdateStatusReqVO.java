package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductCategoryUpdateStatusReqVO {
    @NotNull
    private Long id;
    @NotNull @InEnum(CommonStatusEnum.class)
    private Integer status;
}
