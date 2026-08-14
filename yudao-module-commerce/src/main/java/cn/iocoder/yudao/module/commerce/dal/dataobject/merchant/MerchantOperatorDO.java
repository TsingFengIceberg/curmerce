package cn.iocoder.yudao.module.commerce.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("commerce_merchant_operator")
@KeySequence("commerce_merchant_operator_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantOperatorDO extends BaseDO {
    @TableId
    private Long id;
    private Long merchantId;
    private Long userId;
    private Integer operatorType;
    private Integer status;
}
