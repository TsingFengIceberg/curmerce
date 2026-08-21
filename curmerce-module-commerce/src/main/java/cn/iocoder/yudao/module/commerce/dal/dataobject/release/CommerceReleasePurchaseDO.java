package cn.iocoder.yudao.module.commerce.dal.dataobject.release;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("commerce_release_purchase")
@KeySequence("commerce_release_purchase_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceReleasePurchaseDO extends BaseDO {
    @TableId private Long id;
    private Long campaignId;
    private Long itemId;
    private Long buyerUserId;
    private Long orderId;
    private Integer quantity;
    private Long unitPrice;
    private Integer status;
}
