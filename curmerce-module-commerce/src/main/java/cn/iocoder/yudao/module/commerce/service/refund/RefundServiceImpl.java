package cn.iocoder.yudao.module.commerce.service.refund;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundApplyReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.refund.CommerceRefundMapper;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class RefundServiceImpl implements RefundService {
    private static final DateTimeFormatter REFUND_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private CommerceOrderMapper orderMapper;
    @Resource
    private CommerceRefundMapper refundMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundRespVO applyRefund(Long userId, RefundApplyReqVO reqVO) {
        memberUserApi.validateActiveUserForUpdate(userId);
        CommerceOrderDO order = orderMapper.selectOwnedForUpdate(userId, reqVO.getOrderId());
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        if (!isRefundable(order.getStatus())) {
            throw exception(REFUND_ORDER_NOT_REFUNDABLE);
        }
        if (order.getPayableAmount() == null || order.getPayableAmount() < 0) {
            throw exception(REFUND_AMOUNT_INVALID);
        }

        CommerceRefundDO existing = refundMapper.selectByOrderIdForUpdate(order.getId());
        if (existing != null) {
            return toResponse(existing);
        }

        LocalDateTime requestedTime = LocalDateTime.now();
        CommerceRefundDO refund = new CommerceRefundDO().setRefundNo(generateRefundNo())
                .setOrderId(order.getId()).setOrderNo(order.getOrderNo()).setMemberUserId(userId)
                .setAmount(order.getPayableAmount()).setStatus(RefundStatusEnum.REQUESTED.getStatus())
                .setReason(StrUtil.trim(reqVO.getReason())).setRequestedTime(requestedTime);
        refundMapper.insert(refund);

        // The current payment provider is deliberately simulated. Keep the refund record and state
        // transition explicit so a real provider callback can replace this synchronous success later.
        LocalDateTime processedTime = LocalDateTime.now();
        if (refundMapper.markSuccess(refund.getId(), processedTime) != 1) {
            throw exception(REFUND_STATE_INVALID);
        }
        refund.setStatus(RefundStatusEnum.SUCCESS.getStatus()).setProcessedTime(processedTime);
        return toResponse(refund);
    }

    private boolean isRefundable(Integer status) {
        return OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus().equals(status)
                || OrderStatusEnum.SHIPPED.getStatus().equals(status)
                || OrderStatusEnum.COMPLETED.getStatus().equals(status);
    }

    private String generateRefundNo() {
        return "R" + LocalDateTime.now().format(REFUND_TIME_FORMAT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private RefundRespVO toResponse(CommerceRefundDO refund) {
        return new RefundRespVO().setId(refund.getId()).setRefundNo(refund.getRefundNo())
                .setOrderId(refund.getOrderId()).setOrderNo(refund.getOrderNo()).setAmount(refund.getAmount())
                .setStatus(refund.getStatus()).setReason(refund.getReason())
                .setRequestedTime(refund.getRequestedTime()).setProcessedTime(refund.getProcessedTime());
    }
}
