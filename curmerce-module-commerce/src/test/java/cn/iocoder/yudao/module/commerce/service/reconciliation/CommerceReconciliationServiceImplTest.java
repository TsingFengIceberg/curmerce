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
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.reconciliation.CommerceReconciliationIssueStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommerceReconciliationServiceImplTest {

    @Mock private CommerceOrderMapper orderMapper;
    @Mock private CommercePaymentMapper paymentMapper;
    @Mock private CommerceRefundMapper refundMapper;
    @Mock private CommerceReconciliationIssueMapper issueMapper;
    @InjectMocks private CommerceReconciliationServiceImpl service;

    @Test
    void scanAndOpenIssues_opensPaidOrderStateMismatch() {
        CommercePaymentDO payment = new CommercePaymentDO().setId(9101L).setPaymentNo("P-1")
                .setOrderId(9001L).setOrderNo("C-1").setStatus(PaymentStatusEnum.SUCCESS.getStatus());
        when(paymentMapper.selectSuccessForAudit(100)).thenReturn(List.of(payment));
        when(orderMapper.selectById(9001L)).thenReturn(new CommerceOrderDO().setId(9001L)
                .setOrderNo("C-1").setStatus(OrderStatusEnum.CANCELED.getStatus()));
        when(issueMapper.selectOpenByScope(eq("PAYMENT_ORDER_STATE_MISMATCH"), eq(9001L), eq(9101L), isNull()))
                .thenReturn(null);

        assertEquals(1, service.scanAndOpenIssues(100));

        ArgumentCaptor<CommerceReconciliationIssueDO> captor =
                ArgumentCaptor.forClass(CommerceReconciliationIssueDO.class);
        verify(issueMapper).insert(captor.capture());
        assertEquals("PAYMENT_ORDER_STATE_MISMATCH", captor.getValue().getIssueType());
        assertEquals(9001L, captor.getValue().getOrderId());
        assertEquals(9101L, captor.getValue().getPaymentId());
    }

    @Test
    void scanAndOpenIssues_opensOrderWithoutSuccessPayment() {
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setOrderNo("C-1")
                .setStatus(OrderStatusEnum.COMPLETED.getStatus());
        when(orderMapper.selectPaidOrCompletedForAudit(100)).thenReturn(List.of(order));
        when(paymentMapper.selectByOrderId(9001L)).thenReturn(null);
        when(issueMapper.selectOpenByScope(eq("ORDER_WITHOUT_SUCCESS_PAYMENT"), eq(9001L), isNull(), isNull()))
                .thenReturn(null);

        assertEquals(1, service.scanAndOpenIssues(100));

        ArgumentCaptor<CommerceReconciliationIssueDO> captor =
                ArgumentCaptor.forClass(CommerceReconciliationIssueDO.class);
        verify(issueMapper).insert(captor.capture());
        assertEquals("ORDER_WITHOUT_SUCCESS_PAYMENT", captor.getValue().getIssueType());
        assertEquals(9001L, captor.getValue().getOrderId());
        assertEquals(null, captor.getValue().getPaymentId());
    }

    @Test
    void scanAndOpenIssues_opensRefundOrderMismatch() {
        CommerceRefundDO refund = new CommerceRefundDO().setId(9201L).setRefundNo("R-1")
                .setOrderId(9001L).setOrderNo("C-1").setStatus(RefundStatusEnum.APPROVED.getStatus());
        when(refundMapper.selectActiveOrSuccessForAudit(100)).thenReturn(List.of(refund));
        when(orderMapper.selectById(9001L)).thenReturn(new CommerceOrderDO().setId(9001L)
                .setOrderNo("C-1").setStatus(OrderStatusEnum.SHIPPED.getStatus())
                .setRefundStatus(RefundStatusEnum.REQUESTED.getStatus()));
        when(issueMapper.selectOpenByScope(eq("REFUND_ORDER_STATUS_MISMATCH"), eq(9001L), isNull(), eq(9201L)))
                .thenReturn(null);

        assertEquals(1, service.scanAndOpenIssues(100));

        ArgumentCaptor<CommerceReconciliationIssueDO> captor =
                ArgumentCaptor.forClass(CommerceReconciliationIssueDO.class);
        verify(issueMapper).insert(captor.capture());
        assertEquals("REFUND_ORDER_STATUS_MISMATCH", captor.getValue().getIssueType());
        assertEquals(9201L, captor.getValue().getRefundId());
    }

    @Test
    void scanAndOpenIssues_skipsExistingOpenIssue() {
        CommercePaymentDO payment = new CommercePaymentDO().setId(9101L).setPaymentNo("P-1")
                .setOrderId(9001L).setOrderNo("C-1").setStatus(PaymentStatusEnum.SUCCESS.getStatus());
        when(paymentMapper.selectSuccessForAudit(100)).thenReturn(List.of(payment));
        when(orderMapper.selectById(9001L)).thenReturn(new CommerceOrderDO().setId(9001L)
                .setOrderNo("C-1").setStatus(OrderStatusEnum.CANCELED.getStatus()));
        when(issueMapper.selectOpenByScope(eq("PAYMENT_ORDER_STATE_MISMATCH"), eq(9001L), eq(9101L), isNull()))
                .thenReturn(new CommerceReconciliationIssueDO().setId(1L));

        assertEquals(0, service.scanAndOpenIssues(100));

        verify(issueMapper, never()).insert(any(CommerceReconciliationIssueDO.class));
    }

    @Test
    void resolveIssue_marksResolved() {
        when(issueMapper.markResolved(42L)).thenReturn(1);
        assertTrue(service.resolveIssue(42L));
        verify(issueMapper).markResolved(42L);
    }

    @Test
    void repairIssue_advancesPendingOrderAfterSuccessfulPayment() {
        CommerceReconciliationIssueDO issue = new CommerceReconciliationIssueDO().setId(42L)
                .setIssueType("PAYMENT_ORDER_STATE_MISMATCH").setOrderId(9001L).setPaymentId(9101L)
                .setStatus(CommerceReconciliationIssueStatusEnum.OPEN.getStatus());
        when(issueMapper.selectByIdForUpdate(42L)).thenReturn(issue);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(new CommerceOrderDO().setId(9001L)
                .setStatus(OrderStatusEnum.PENDING_PAYMENT.getStatus()));
        when(paymentMapper.selectByIdForUpdate(9101L)).thenReturn(new CommercePaymentDO().setId(9101L)
                .setStatus(PaymentStatusEnum.SUCCESS.getStatus()));
        when(orderMapper.markPaid(9001L)).thenReturn(1);
        when(issueMapper.markResolved(42L)).thenReturn(1);

        assertTrue(service.repairIssue(42L));
        verify(orderMapper).markPaid(9001L);
        verify(issueMapper).markResolved(42L);
    }

    @Test
    void repairIssue_doesNotInventPaymentForCanceledOrder() {
        CommerceReconciliationIssueDO issue = new CommerceReconciliationIssueDO().setId(42L)
                .setIssueType("PAYMENT_ORDER_STATE_MISMATCH").setOrderId(9001L).setPaymentId(9101L)
                .setStatus(CommerceReconciliationIssueStatusEnum.OPEN.getStatus());
        when(issueMapper.selectByIdForUpdate(42L)).thenReturn(issue);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(new CommerceOrderDO().setId(9001L)
                .setStatus(OrderStatusEnum.CANCELED.getStatus()));
        when(paymentMapper.selectByIdForUpdate(9101L)).thenReturn(new CommercePaymentDO().setId(9101L)
                .setStatus(PaymentStatusEnum.SUCCESS.getStatus()));

        assertTrue(!service.repairIssue(42L));
        verify(orderMapper, never()).markPaid(anyLong());
        verify(issueMapper, never()).markResolved(42L);
    }
}
