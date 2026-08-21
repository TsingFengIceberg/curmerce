package cn.iocoder.yudao.module.commerce.controller.app.auction.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuctionRespVO {
    private Long id;
    private String name;
    private Long productId;
    private Long skuId;
    private Integer status;
    private Long startingPrice;
    private Long minIncrement;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long currentAmount;
    private Long currentBidderUserId;
    private Long winnerUserId;
    private Long winningBidId;
    private LocalDateTime settlementFailedTime;
    private String settlementFailureReason;
}
