package cn.iocoder.yudao.module.commerce.controller.app.auction.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AuctionPageReqVO extends PageParam {
    private String keyword;
}
