package cn.iocoder.yudao.module.commerce.controller.app.catalog.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import org.hibernate.validator.constraints.Length;

@Data
@EqualsAndHashCode(callSuper = true)
public class PublicProductPageReqVO extends PageParam {
    private Long categoryId;
    @Length(max = 64)
    private String keyword;
    @Min(0)
    private Long minPrice;
    @Min(0)
    private Long maxPrice;
    private Boolean inStock;
    @Min(1) @Max(2)
    private Integer sellerType;
    @Length(max = 64)
    private String storeKeyword;
    @Pattern(regexp = "latest|priceAsc|priceDesc", message = "排序方式不正确")
    private String sort;

    @AssertTrue(message = "最低价格不能高于最高价格")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }
}
