package cn.iocoder.yudao.module.commerce.controller.app.auction.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuctionBidPageReqVO extends PageParam {
    @NotNull
    private Long sessionId;
}
