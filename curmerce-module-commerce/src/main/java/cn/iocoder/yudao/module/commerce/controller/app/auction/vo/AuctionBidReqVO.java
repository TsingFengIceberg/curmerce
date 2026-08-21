package cn.iocoder.yudao.module.commerce.controller.app.auction.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuctionBidReqVO {
    @NotNull private Long sessionId;
    @NotNull @Min(0) private Long amount;
    @NotBlank private String idempotencyKey;
}
