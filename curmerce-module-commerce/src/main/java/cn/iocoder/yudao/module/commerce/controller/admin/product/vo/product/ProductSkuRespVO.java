package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import lombok.Data;

import java.util.List;

@Data
public class ProductSkuRespVO {
    private Long id;
    private Long productId;
    private String code;
    private List<ProductSpecificationValueRespVO> specificationValues;
    private String imageUrl;
    private Long price;
    private Long marketPrice;
    private Integer stock;
    private Integer status;
    private Integer sort;
}
