package cn.iocoder.yudao.module.commerce.controller.app.personal.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalListingPageReqVO extends PageParam {
    @InEnum(ProductAuditStatusEnum.class) private Integer auditStatus;
    @InEnum(ProductSaleStatusEnum.class) private Integer saleStatus;
}
