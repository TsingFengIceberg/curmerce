package cn.iocoder.yudao.module.commerce.controller.app.auction.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuctionBidRespVO {
    private Long id;
    private Long amount;
    private String bidderLabel;
    private Boolean mine;
    private Boolean leading;
    private LocalDateTime createTime;
}
