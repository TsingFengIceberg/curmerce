package cn.iocoder.yudao.curmerce.cloud.api;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CoreAuctionItemCheckReqDTO {
    @NotNull private Long userId;
    @NotNull private Long productId;
    @NotNull private Long skuId;
}
