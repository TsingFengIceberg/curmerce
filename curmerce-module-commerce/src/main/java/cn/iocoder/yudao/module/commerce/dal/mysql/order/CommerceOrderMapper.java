package cn.iocoder.yudao.module.commerce.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommerceOrderMapper extends BaseMapperX<CommerceOrderDO> {
    default CommerceOrderDO selectByUserAndIdempotencyKey(Long userId, String key) {
        return selectOne(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getMemberUserId, userId)
                .eq(CommerceOrderDO::getIdempotencyKey, key));
    }
    default CommerceOrderDO selectOwned(Long userId, Long id) {
        return selectOne(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                .eq(CommerceOrderDO::getMemberUserId, userId));
    }
    default CommerceOrderDO selectOwnedForUpdate(Long userId, Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                .eq(CommerceOrderDO::getMemberUserId, userId));
    }
    default CommerceOrderDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id));
    }
    default CommerceOrderDO selectOwnedForUpdate(Long id, Long merchantId, Long storeId) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                .eq(CommerceOrderDO::getMerchantId, merchantId).eq(CommerceOrderDO::getStoreId, storeId));
    }
    default int markPaid(Long id) {
        return update(new CommerceOrderDO().setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus()),
                new LambdaUpdateWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                        .eq(CommerceOrderDO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getStatus()));
    }
    default PageResult<CommerceOrderDO> selectPageOwned(Long userId, PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceOrderDO>()
                .eq(CommerceOrderDO::getMemberUserId, userId)
                .orderByDesc(CommerceOrderDO::getId));
    }

    default PageResult<CommerceOrderDO> selectPagePendingShipment(MerchantOrderPageReqVO req,
                                                                    Long merchantId, Long storeId) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceOrderDO>()
                .eq(CommerceOrderDO::getMerchantId, merchantId)
                .eq(CommerceOrderDO::getStoreId, storeId)
                .eq(CommerceOrderDO::getStatus, OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus())
                .orderByDesc(CommerceOrderDO::getId));
    }

    default int markShipped(Long id, Long merchantId, Long storeId, String logisticsCompany,
                            String trackingNo, LocalDateTime shippingTime) {
        return update(new CommerceOrderDO().setStatus(OrderStatusEnum.SHIPPED.getStatus())
                        .setShippingTime(shippingTime).setLogisticsCompany(logisticsCompany)
                        .setTrackingNo(trackingNo),
                new LambdaUpdateWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                        .eq(CommerceOrderDO::getMerchantId, merchantId)
                        .eq(CommerceOrderDO::getStoreId, storeId)
                        .eq(CommerceOrderDO::getStatus, OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus()));
    }

    default int markCompleted(Long userId, Long id, LocalDateTime completionTime) {
        return update(new CommerceOrderDO().setStatus(OrderStatusEnum.COMPLETED.getStatus())
                        .setCompletionTime(completionTime),
                new LambdaUpdateWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                        .eq(CommerceOrderDO::getMemberUserId, userId)
                        .eq(CommerceOrderDO::getStatus, OrderStatusEnum.SHIPPED.getStatus()));
    }

    default int markCanceled(Long userId, Long id) {
        return update(new CommerceOrderDO().setStatus(OrderStatusEnum.CANCELED.getStatus()),
                new LambdaUpdateWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                        .eq(CommerceOrderDO::getMemberUserId, userId)
                        .eq(CommerceOrderDO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getStatus()));
    }

    default int markCanceled(Long id) {
        return update(new CommerceOrderDO().setStatus(OrderStatusEnum.CANCELED.getStatus()),
                new LambdaUpdateWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                        .eq(CommerceOrderDO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getStatus()));
    }

    default List<CommerceOrderDO> selectExpiredPendingPaymentForUpdate(LocalDateTime cutoffTime, int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 1000));
        return selectList(new LambdaQueryWrapper<CommerceOrderDO>()
                .eq(CommerceOrderDO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .isNotNull(CommerceOrderDO::getPaymentDeadline)
                .le(CommerceOrderDO::getPaymentDeadline, cutoffTime)
                .orderByAsc(CommerceOrderDO::getId)
                .last("LIMIT " + safeBatchSize + " FOR UPDATE"));
    }
}
