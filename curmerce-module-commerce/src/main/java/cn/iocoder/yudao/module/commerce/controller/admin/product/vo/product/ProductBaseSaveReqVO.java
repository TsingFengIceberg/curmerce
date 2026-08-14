package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProductBaseSaveReqVO {
    @NotNull
    private Long storeId;
    @NotNull
    private Long categoryId;
    @NotBlank @Size(max = 128)
    private String name;
    @Size(max = 255)
    private String subtitle;
    @NotBlank @Size(max = 1024)
    private String mainImageUrl;
    @Size(max = 20)
    private List<@Size(max = 1024) String> imageUrls;
    @NotBlank @Size(max = 100000)
    private String description;
    @NotNull @Min(0)
    private Integer sort;
    @NotEmpty @Size(max = 100)
    private List<@Valid ProductSkuSaveReqVO> skus;
}
