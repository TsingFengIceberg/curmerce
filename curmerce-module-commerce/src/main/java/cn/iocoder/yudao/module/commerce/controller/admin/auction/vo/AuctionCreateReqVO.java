package cn.iocoder.yudao.module.commerce.controller.admin.auction.vo;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuctionCreateReqVO {
    @NotBlank private String name;
    @NotNull private Long productId;
    @NotNull private Long skuId;
    @NotNull @Min(0) private Long startingPrice;
    @NotNull @Min(1) private Long minIncrement;
    @NotNull @Future private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
}
