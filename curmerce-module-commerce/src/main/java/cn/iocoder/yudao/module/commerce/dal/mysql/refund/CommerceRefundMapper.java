package cn.iocoder.yudao.module.commerce.dal.mysql.refund;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface CommerceRefundMapper extends BaseMapperX<CommerceRefundDO> {

    default CommerceRefundDO selectByOrderIdForUpdate(Long orderId) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceRefundDO>()
                .eq(CommerceRefundDO::getOrderId, orderId));
    }

    default int markSuccess(Long id, LocalDateTime processedTime) {
        return update(new CommerceRefundDO().setStatus(RefundStatusEnum.SUCCESS.getStatus())
                        .setProcessedTime(processedTime),
                new LambdaUpdateWrapper<CommerceRefundDO>().eq(CommerceRefundDO::getId, id)
                        .eq(CommerceRefundDO::getStatus, RefundStatusEnum.REQUESTED.getStatus()));
    }
}
