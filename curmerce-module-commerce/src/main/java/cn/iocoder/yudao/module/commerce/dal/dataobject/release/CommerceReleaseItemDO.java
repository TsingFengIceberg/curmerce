package cn.iocoder.yudao.module.commerce.dal.dataobject.release;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("commerce_release_item")
@KeySequence("commerce_release_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceReleaseItemDO extends BaseDO {
    @TableId private Long id;
    private Long campaignId;
    private Long productId;
    private Long skuId;
    private Long campaignPrice;
    private Integer stock;
    private Integer soldCount;
}
