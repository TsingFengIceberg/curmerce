package cn.iocoder.yudao.module.commerce.controller.admin.auction.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AuctionPageReqVO extends PageParam {
    private Integer status;
    private String name;
}
