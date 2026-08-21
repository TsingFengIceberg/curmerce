package cn.iocoder.yudao.module.commerce.controller.app.catalog.vo;

import lombok.Data;

@Data
public class PublicProductSummaryRespVO {
    private Long id;
    private Long categoryId;
    private Long storeId;
    private String storeName;
    private Integer sellerType;
    private Long sellerUserId;
    private String sellerName;
    private String name;
    private String condition;
    private String subtitle;
    private String mainImageUrl;
    private Long minPrice;
    private Long minMarketPrice;
    private Integer totalStock;
    private Boolean available;
}
