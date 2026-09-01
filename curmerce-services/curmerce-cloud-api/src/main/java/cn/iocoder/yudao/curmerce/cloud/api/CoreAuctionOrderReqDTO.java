package cn.iocoder.yudao.curmerce.cloud.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CoreAuctionOrderReqDTO {
    @NotNull private Long userId;
    @NotNull private Long addressId;
    @NotNull private Long productId;
    @NotNull private Long skuId;
    @NotNull @Min(0) private Long amount;
    @NotNull private Long sessionId;
}
