package cn.iocoder.yudao.module.commerce.dal.mysql.refund;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

@Mapper
public interface CommerceRefundMapper extends BaseMapperX<CommerceRefundDO> {

    default CommerceRefundDO selectByOrderIdForUpdate(Long orderId) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceRefundDO>()
                .eq(CommerceRefundDO::getOrderId, orderId));
    }

    default CommerceRefundDO selectByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapper<CommerceRefundDO>()
                .eq(CommerceRefundDO::getOrderId, orderId));
    }

    default CommerceRefundDO selectOwned(Long userId, Long id) {
        return selectOne(new LambdaQueryWrapper<CommerceRefundDO>().eq(CommerceRefundDO::getId, id)
                .eq(CommerceRefundDO::getMemberUserId, userId));
    }

    default PageResult<CommerceRefundDO> selectPageOwned(Long userId, PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceRefundDO>()
                .eq(CommerceRefundDO::getMemberUserId, userId)
                .orderByDesc(CommerceRefundDO::getId));
    }

    default int markSuccess(Long id, LocalDateTime processedTime) {
        return update(new CommerceRefundDO().setStatus(RefundStatusEnum.SUCCESS.getStatus())
                        .setProcessedTime(processedTime),
                new LambdaUpdateWrapper<CommerceRefundDO>().eq(CommerceRefundDO::getId, id)
                        .eq(CommerceRefundDO::getStatus, RefundStatusEnum.REQUESTED.getStatus()));
    }
}
