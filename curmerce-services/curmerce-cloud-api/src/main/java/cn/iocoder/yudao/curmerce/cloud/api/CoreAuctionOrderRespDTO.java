package cn.iocoder.yudao.curmerce.cloud.api;

import lombok.Data;

@Data
public class CoreAuctionOrderRespDTO {
    private Long orderId;
    private String orderNo;
    private Integer status;
}
