package cn.iocoder.yudao.module.commerce.dal.mysql.reconciliation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.reconciliation.CommerceReconciliationIssueDO;
import cn.iocoder.yudao.module.commerce.enums.reconciliation.CommerceReconciliationIssueStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommerceReconciliationIssueMapper extends BaseMapperX<CommerceReconciliationIssueDO> {

    default CommerceReconciliationIssueDO selectOpenByScope(String issueType, Long orderId,
                                                            Long paymentId, Long refundId) {
        return selectOne(new LambdaQueryWrapper<CommerceReconciliationIssueDO>()
                .eq(CommerceReconciliationIssueDO::getIssueType, issueType)
                .eq(CommerceReconciliationIssueDO::getOrderId, orderId)
                .eq(CommerceReconciliationIssueDO::getPaymentId, paymentId)
                .eq(CommerceReconciliationIssueDO::getRefundId, refundId)
                .eq(CommerceReconciliationIssueDO::getStatus, CommerceReconciliationIssueStatusEnum.OPEN.getStatus()));
    }

    default int markResolved(Long id) {
        return update(new CommerceReconciliationIssueDO()
                        .setStatus(CommerceReconciliationIssueStatusEnum.RESOLVED.getStatus()),
                new LambdaUpdateWrapper<CommerceReconciliationIssueDO>().eq(CommerceReconciliationIssueDO::getId, id)
                        .eq(CommerceReconciliationIssueDO::getStatus, CommerceReconciliationIssueStatusEnum.OPEN.getStatus()));
    }
}
