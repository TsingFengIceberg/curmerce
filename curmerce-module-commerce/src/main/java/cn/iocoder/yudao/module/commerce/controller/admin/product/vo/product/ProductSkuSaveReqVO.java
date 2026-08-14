package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProductSkuSaveReqVO {
    private Long id;
    @NotBlank @Size(min = 2, max = 64)
    private String code;
    @Size(max = 20)
    private List<@Valid ProductSpecificationValueReqVO> specificationValues;
    @Size(max = 1024)
    private String imageUrl;
    @NotNull @Min(0)
    private Long price;
    @Min(0)
    private Long marketPrice;
    @NotNull @Min(0)
    private Integer stock;
    @NotNull @InEnum(CommonStatusEnum.class)
    private Integer status;
    @NotNull @Min(0)
    private Integer sort;
}
