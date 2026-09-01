package cn.iocoder.yudao.curmerce.cloud.api;

import lombok.Data;

@Data
public class CoreAuctionItemCheckRespDTO {
    private Long merchantId;
    private Long storeId;
    private Long productId;
    private Long skuId;
    private String productName;
    private String productImageUrl;
    private String skuLabel;
    private Long skuPrice;
    private Integer skuStock;
}
