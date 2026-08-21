package cn.iocoder.yudao.module.commerce.controller.app.auction.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuctionSettleReqVO {
    @NotNull private Long sessionId;
    @NotNull private Long addressId;
}
