package cn.iocoder.yudao.module.commerce.dal.dataobject.auction;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("commerce_auction_session")
@KeySequence("commerce_auction_session_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceAuctionSessionDO extends BaseDO {
    @TableId private Long id;
    private Long merchantId;
    private Long storeId;
    private Long productId;
    private Long skuId;
    private String name;
    private Integer status;
    private Long startingPrice;
    private Long minIncrement;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long winnerUserId;
    private Long winningBidId;
    private Long settlementOrderId;
}
