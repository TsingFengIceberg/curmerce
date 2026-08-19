package cn.iocoder.yudao.module.commerce.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.payment.CommercePaymentDO;
import cn.iocoder.yudao.module.commerce.enums.payment.PaymentStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommercePaymentMapper extends BaseMapperX<CommercePaymentDO> {

    default CommercePaymentDO selectByOrderIdForUpdate(Long orderId) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommercePaymentDO>()
                .eq(CommercePaymentDO::getOrderId, orderId));
    }

    default CommercePaymentDO selectByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapper<CommercePaymentDO>()
                .eq(CommercePaymentDO::getOrderId, orderId));
    }

    default CommercePaymentDO selectByPaymentNo(String paymentNo) {
        return selectOne(new LambdaQueryWrapper<CommercePaymentDO>()
                .eq(CommercePaymentDO::getPaymentNo, paymentNo));
    }

    default CommercePaymentDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommercePaymentDO>()
                .eq(CommercePaymentDO::getId, id));
    }

    default int markSuccess(Long id, String callbackId, LocalDateTime paidTime) {
        return update(new CommercePaymentDO().setStatus(PaymentStatusEnum.SUCCESS.getStatus())
                        .setCallbackId(callbackId).setPaidTime(paidTime),
                new LambdaUpdateWrapper<CommercePaymentDO>().eq(CommercePaymentDO::getId, id)
                        .eq(CommercePaymentDO::getStatus, PaymentStatusEnum.INITIATED.getStatus()));
    }

    default int markCanceled(Long id) {
        return update(new CommercePaymentDO().setStatus(PaymentStatusEnum.CANCELED.getStatus()),
                new LambdaUpdateWrapper<CommercePaymentDO>().eq(CommercePaymentDO::getId, id)
                        .eq(CommercePaymentDO::getStatus, PaymentStatusEnum.INITIATED.getStatus()));
    }

    /** 对账用：按创建顺序取一批已成功支付单。 */
    default List<CommercePaymentDO> selectSuccessForAudit(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return selectList(new LambdaQueryWrapper<CommercePaymentDO>()
                .eq(CommercePaymentDO::getStatus, PaymentStatusEnum.SUCCESS.getStatus())
                .orderByAsc(CommercePaymentDO::getId)
                .last("LIMIT " + safeLimit));
    }
}
