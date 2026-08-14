package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductSpecificationValueReqVO {
    @NotBlank @Size(max = 50)
    private String name;
    @NotBlank @Size(max = 50)
    private String value;
}
