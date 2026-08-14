package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductCategoryCreateReqVO {
    private Long parentId;
    @NotBlank @Size(min = 2, max = 32)
    private String code;
    @NotBlank @Size(max = 64)
    private String name;
    @Size(max = 1024)
    private String imageUrl;
    @Min(0)
    private Integer sort = 0;
}
