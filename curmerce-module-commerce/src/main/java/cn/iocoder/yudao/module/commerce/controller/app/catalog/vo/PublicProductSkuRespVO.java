package cn.iocoder.yudao.module.commerce.controller.app.catalog.vo;

import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import lombok.Data;
import java.util.List;

@Data
public class PublicProductSkuRespVO {
    private Long id;
    private List<ProductSkuDO.SpecificationValue> specificationValues;
    private String imageUrl;
    private Long price;
    private Long marketPrice;
    private Integer stock;
    private Boolean available;
}
