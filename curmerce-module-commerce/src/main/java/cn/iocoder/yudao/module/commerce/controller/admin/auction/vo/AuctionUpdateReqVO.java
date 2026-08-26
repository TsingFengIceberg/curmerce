package cn.iocoder.yudao.module.commerce.controller.admin.auction.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuctionUpdateReqVO extends AuctionCreateReqVO {

    @NotNull
    private Long id;

}
