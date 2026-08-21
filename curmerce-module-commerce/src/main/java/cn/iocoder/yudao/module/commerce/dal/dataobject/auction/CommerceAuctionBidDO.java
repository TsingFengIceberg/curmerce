package cn.iocoder.yudao.module.commerce.dal.dataobject.auction;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("commerce_auction_bid")
@KeySequence("commerce_auction_bid_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceAuctionBidDO extends BaseDO {
    @TableId private Long id;
    private Long sessionId;
    private Long bidderUserId;
    private Long amount;
    private String idempotencyKey;
}
