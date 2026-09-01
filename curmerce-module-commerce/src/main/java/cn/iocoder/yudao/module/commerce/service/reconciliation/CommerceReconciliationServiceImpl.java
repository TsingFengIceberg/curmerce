package cn.iocoder.yudao.module.commerce.service.reconciliation;

import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.payment.CommercePaymentDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.reconciliation.CommerceReconciliationIssueDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.payment.CommercePaymentMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.reconciliation.CommerceReconciliationIssueMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.refund.CommerceRefundMapper;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.payment.PaymentStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.reconciliation.CommerceReconciliationIssueStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.reconciliation.CommerceReconciliationIssueTypeEnum;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class CommerceReconciliationServiceImpl implements CommerceReconciliationService {

    @Resource
    private CommerceOrderMapper orderMapper;
    @Resource
    private CommercePaymentMapper paymentMapper;
    @Resource
    private CommerceRefundMapper refundMapper;
    @Resource
    private CommerceReconciliationIssueMapper issueMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanAndOpenIssues(int batchSize) {
        int opened = 0;
        opened += scanPaidOrderStateMismatches(batchSize);
        opened += scanOrdersWithoutSuccessPayment(batchSize);
        opened += scanRefundOrderMismatches(batchSize);
        return opened;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resolveIssue(Long id) {
        return issueMapper.markResolved(id) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean repairIssue(Long id) {
        CommerceReconciliationIssueDO issue = issueMapper.selectByIdForUpdate(id);
        if (issue == null || !CommerceReconciliationIssueStatusEnum.OPEN.getStatus().equals(issue.getStatus())) {
            return false;
        }
        boolean repaired = switch (issue.getIssueType()) {
            case "PAYMENT_ORDER_STATE_MISMATCH" -> repairPaymentOrderState(issue);
            case "REFUND_ORDER_STATUS_MISMATCH" -> repairRefundOrderState(issue);
            default -> false;
        };
        return repaired && issueMapper.markResolved(id) == 1;
    }

    /** A successful payment may safely advance only a still-pending order. */
    private boolean repairPaymentOrderState(CommerceReconciliationIssueDO issue) {
        if (issue.getOrderId() == null || issue.getPaymentId() == null) {
            return false;
        }
        CommerceOrderDO order = orderMapper.selectByIdForUpdate(issue.getOrderId());
        CommercePaymentDO payment = paymentMapper.selectByIdForUpdate(issue.getPaymentId());
        if (order == null || payment == null
                || !PaymentStatusEnum.SUCCESS.getStatus().equals(payment.getStatus())
                || !OrderStatusEnum.PENDING_PAYMENT.getStatus().equals(order.getStatus())) {
            return false;
        }
        return orderMapper.markPaid(order.getId()) == 1;
    }

    /** Refund reconciliation repairs only the denormalized order after-sale status. */
    private boolean repairRefundOrderState(CommerceReconciliationIssueDO issue) {
        if (issue.getOrderId() == null || issue.getRefundId() == null) {
            return false;
        }
        CommerceOrderDO order = orderMapper.selectByIdForUpdate(issue.getOrderId());
        CommerceRefundDO refund = refundMapper.selectByIdForUpdate(issue.getRefundId());
        if (order == null || refund == null || refund.getStatus() == null
                || (!RefundStatusEnum.REQUESTED.getStatus().equals(refund.getStatus())
                && !RefundStatusEnum.APPROVED.getStatus().equals(refund.getStatus())
                && !RefundStatusEnum.SUCCESS.getStatus().equals(refund.getStatus()))) {
            return false;
        }
        return orderMapper.markRefundStatus(order.getId(), refund.getStatus()) == 1;
    }

    private int scanPaidOrderStateMismatches(int batchSize) {
        int opened = 0;
        List<CommercePaymentDO> payments = paymentMapper.selectSuccessForAudit(batchSize);
        for (CommercePaymentDO payment : payments) {
            CommerceOrderDO order = orderMapper.selectById(payment.getOrderId());
            boolean orderStateInvalid = order == null
                    || OrderStatusEnum.PENDING_PAYMENT.getStatus().equals(order.getStatus())
                    || OrderStatusEnum.CANCELED.getStatus().equals(order.getStatus());
            if (!orderStateInvalid) {
                continue;
            }
            String description = "支付单(" + payment.getPaymentNo() + ") 已成功，但订单("
                    + payment.getOrderNo() + ") 状态为 "
                    + (order == null ? "不存在" : order.getStatus());
            if (openIfAbsent(CommerceReconciliationIssueTypeEnum.PAYMENT_ORDER_STATE_MISMATCH,
                    payment.getOrderId(), payment.getId(), null, description)) {
                opened++;
            }
        }
        return opened;
    }

    private int scanOrdersWithoutSuccessPayment(int batchSize) {
        int opened = 0;
        List<CommerceOrderDO> orders = orderMapper.selectPaidOrCompletedForAudit(batchSize);
        for (CommerceOrderDO order : orders) {
            CommercePaymentDO payment = paymentMapper.selectByOrderId(order.getId());
            if (payment != null && PaymentStatusEnum.SUCCESS.getStatus().equals(payment.getStatus())) {
                continue;
            }
            String description = "订单(" + order.getOrderNo() + ") 已履约（状态 "
                    + order.getStatus() + "），但没有成功支付单";
            if (openIfAbsent(CommerceReconciliationIssueTypeEnum.ORDER_WITHOUT_SUCCESS_PAYMENT,
                    order.getId(), payment == null ? null : payment.getId(), null, description)) {
                opened++;
            }
        }
        return opened;
    }

    private int scanRefundOrderMismatches(int batchSize) {
        int opened = 0;
        List<CommerceRefundDO> refunds = refundMapper.selectActiveOrSuccessForAudit(batchSize);
        for (CommerceRefundDO refund : refunds) {
            CommerceOrderDO order = orderMapper.selectById(refund.getOrderId());
            boolean mismatched = order == null
                    || !Objects.equals(order.getRefundStatus(), refund.getStatus());
            if (!mismatched) {
                continue;
            }
            String description = "退款单(" + refund.getRefundNo() + ") 状态为 " + refund.getStatus()
                    + "，但订单售后状态为 " + (order == null ? "不存在" : order.getRefundStatus());
            if (openIfAbsent(CommerceReconciliationIssueTypeEnum.REFUND_ORDER_STATUS_MISMATCH,
                    refund.getOrderId(), null, refund.getId(), description)) {
                opened++;
            }
        }
        return opened;
    }

    private boolean openIfAbsent(CommerceReconciliationIssueTypeEnum type, Long orderId,
                                 Long paymentId, Long refundId, String description) {
        CommerceReconciliationIssueDO existing = issueMapper.selectOpenByScope(type.name(),
                orderId, paymentId, refundId);
        if (existing != null) {
            return false;
        }
        issueMapper.insert(new CommerceReconciliationIssueDO()
                .setIssueType(type.name())
                .setOrderId(orderId)
                .setPaymentId(paymentId)
                .setRefundId(refundId)
                .setDescription(description)
                .setStatus(CommerceReconciliationIssueStatusEnum.OPEN.getStatus()));
        log.warn("[scanAndOpenIssues][{}] {}", type.name(), description);
        return true;
    }
}
