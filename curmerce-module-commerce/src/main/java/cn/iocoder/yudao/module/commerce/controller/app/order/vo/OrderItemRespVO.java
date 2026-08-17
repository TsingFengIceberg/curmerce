package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import lombok.Data;
import java.util.List;

@Data
public class OrderItemRespVO {
    private Long id;
    private Long productId;
    private Long skuId;
    private String productName;
    private String productImageUrl;
    private String skuCode;
    private List<ProductSkuDO.SpecificationValue> specificationValues;
    private String skuImageUrl;
    private Long price;
    private Integer quantity;
    private Long totalAmount;
}
