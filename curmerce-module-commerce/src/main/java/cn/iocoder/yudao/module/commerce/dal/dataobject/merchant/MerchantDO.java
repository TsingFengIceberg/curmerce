package cn.iocoder.yudao.module.commerce.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("commerce_merchant")
@KeySequence("commerce_merchant_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantDO extends BaseDO {
    @TableId
    private Long id;
    private String name;
    private String code;
    private String contactName;
    private String contactMobile;
    private String defaultStoreName;
    private String defaultStoreCode;
    private Integer status;
    private Long ownerUserId;
    private Long reviewerUserId;
    private LocalDateTime reviewTime;
    private String rejectReason;

    public boolean isPending() {
        return MerchantAuditStatusEnum.PENDING.getStatus().equals(status);
    }
}
