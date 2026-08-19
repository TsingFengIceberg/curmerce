package cn.iocoder.yudao.module.commerce.dal.mysql.refund;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundPageReqVO;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
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

    default CommerceRefundDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceRefundDO>()
                .eq(CommerceRefundDO::getId, id));
    }

    default CommerceRefundDO selectByRefundNoForUpdate(String refundNo) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceRefundDO>()
                .eq(CommerceRefundDO::getRefundNo, refundNo));
    }

    default CommerceRefundDO selectOwned(Long userId, Long id) {
        return selectOne(new LambdaQueryWrapper<CommerceRefundDO>().eq(CommerceRefundDO::getId, id)
                .eq(CommerceRefundDO::getMemberUserId, userId));
    }

    default PageResult<CommerceRefundDO> selectPageOwned(Long userId, RefundPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceRefundDO>()
                .eq(CommerceRefundDO::getMemberUserId, userId)
                .eqIfPresent(CommerceRefundDO::getStatus, req.getStatus())
                .eqIfPresent(CommerceRefundDO::getOrderNo, req.getOrderNo())
                .orderByDesc(CommerceRefundDO::getId));
    }

    default PageResult<CommerceRefundDO> selectPageAdmin(
            cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceRefundDO>()
                .eqIfPresent(CommerceRefundDO::getStatus, req.getStatus())
                .eqIfPresent(CommerceRefundDO::getOrderNo, req.getOrderNo())
                .eqIfPresent(CommerceRefundDO::getMemberUserId, req.getMemberUserId())
                .orderByDesc(CommerceRefundDO::getId));
    }

    default PageResult<CommerceRefundDO> selectPageMerchant(
            cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundPageReqVO req,
            Long merchantId, Long storeId) {
        String orderSubquery = "SELECT id FROM commerce_order WHERE merchant_id = " + merchantId
                + " AND store_id = " + storeId;
        return selectPage(req, new LambdaQueryWrapperX<CommerceRefundDO>()
                .eqIfPresent(CommerceRefundDO::getStatus, req.getStatus())
                .eqIfPresent(CommerceRefundDO::getOrderNo, req.getOrderNo())
                .inSql(CommerceRefundDO::getOrderId, orderSubquery)
                .orderByDesc(CommerceRefundDO::getId));
    }

    default int markApproved(Long id, Long reviewerUserId, LocalDateTime reviewedTime, String reviewRemark) {
        return update(new CommerceRefundDO().setStatus(RefundStatusEnum.APPROVED.getStatus())
                        .setReviewerUserId(reviewerUserId).setReviewedTime(reviewedTime)
                        .setReviewRemark(reviewRemark),
                new LambdaUpdateWrapper<CommerceRefundDO>().eq(CommerceRefundDO::getId, id)
                        .eq(CommerceRefundDO::getStatus, RefundStatusEnum.REQUESTED.getStatus()));
    }

    default int markRejected(Long id, Long reviewerUserId, LocalDateTime reviewedTime, String reviewRemark) {
        return update(new CommerceRefundDO().setStatus(RefundStatusEnum.REJECTED.getStatus())
                        .setReviewerUserId(reviewerUserId).setReviewedTime(reviewedTime)
                        .setReviewRemark(reviewRemark).setProcessedTime(reviewedTime),
                new LambdaUpdateWrapper<CommerceRefundDO>().eq(CommerceRefundDO::getId, id)
                        .eq(CommerceRefundDO::getStatus, RefundStatusEnum.REQUESTED.getStatus()));
    }

    default int markCallback(Long id, String callbackId, boolean callbackSuccess, LocalDateTime processedTime) {
        return update(new CommerceRefundDO().setStatus(callbackSuccess
                                ? RefundStatusEnum.SUCCESS.getStatus() : RefundStatusEnum.FAILED.getStatus())
                        .setCallbackId(callbackId).setCallbackSuccess(callbackSuccess)
                        .setProcessedTime(processedTime),
                new LambdaUpdateWrapper<CommerceRefundDO>().eq(CommerceRefundDO::getId, id)
                        .eq(CommerceRefundDO::getStatus, RefundStatusEnum.APPROVED.getStatus()));
    }

    /** 对账用：取一批申请中、已通过或已成功的退款。 */
    default List<CommerceRefundDO> selectActiveOrSuccessForAudit(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return selectList(new LambdaQueryWrapper<CommerceRefundDO>()
                .in(CommerceRefundDO::getStatus, RefundStatusEnum.REQUESTED.getStatus(),
                        RefundStatusEnum.APPROVED.getStatus(), RefundStatusEnum.SUCCESS.getStatus())
                .orderByAsc(CommerceRefundDO::getId)
                .last("LIMIT " + safeLimit));
    }
}
