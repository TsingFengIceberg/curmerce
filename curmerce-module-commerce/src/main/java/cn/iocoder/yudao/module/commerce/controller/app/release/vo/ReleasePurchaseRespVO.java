package cn.iocoder.yudao.module.commerce.controller.app.release.vo;

import lombok.Data;

@Data
public class ReleasePurchaseRespVO {
    private Long purchaseId;
    private Long campaignId;
    private Long itemId;
    private Integer quantity;
    private Long unitPrice;
    private Long orderId;
    private String orderNo;
    private Integer orderStatus;
}
