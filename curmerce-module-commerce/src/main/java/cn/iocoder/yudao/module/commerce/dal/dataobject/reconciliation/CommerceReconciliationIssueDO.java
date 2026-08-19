package cn.iocoder.yudao.module.commerce.dal.dataobject.reconciliation;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("commerce_reconciliation_issue")
@KeySequence("commerce_reconciliation_issue_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceReconciliationIssueDO extends BaseDO {
    @TableId private Long id;
    private String issueType;
    private Long orderId;
    private Long paymentId;
    private Long refundId;
    private String description;
    private Integer status;
}
