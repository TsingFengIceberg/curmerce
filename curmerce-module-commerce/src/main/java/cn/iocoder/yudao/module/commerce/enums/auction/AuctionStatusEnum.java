package cn.iocoder.yudao.module.commerce.enums.auction;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuctionStatusEnum {
    DRAFT(0, "草稿"),
    SCHEDULED(10, "待开始"),
    RUNNING(20, "进行中"),
    ENDED(30, "已结束"),
    CANCELED(40, "已取消");

    private final Integer status;
    private final String name;
}
