package cn.iocoder.yudao.module.commerce.service.payment;

import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCreateRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCallbackRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentSimulateCallbackReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.payment.CommercePaymentDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.payment.CommercePaymentMapper;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.payment.PaymentStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxEventTypeEnum;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxEventAppender;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private MemberUserApi memberUserApi;
    @Mock
    private CommerceOrderMapper orderMapper;
    @Mock
    private CommercePaymentMapper paymentMapper;
    @Mock
    private CommerceOutboxEventAppender outboxEventAppender;
    @InjectMocks
    private PaymentServiceImpl service;

    @Test
    void createPayment_createsOneIntentWithAuthoritativeOrderAmount() {
        CommerceOrderDO order = pendingOrder();
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order);
        when(paymentMapper.selectByOrderIdForUpdate(9001L)).thenReturn(null);
        when(paymentMapper.insert(any(CommercePaymentDO.class))).thenAnswer(invocation -> {
            invocation.<CommercePaymentDO>getArgument(0).setId(9101L);
            return 1;
        });

        PaymentCreateReqVO req = new PaymentCreateReqVO().setOrderId(9001L).setPaymentMethod(" simulated ");
        PaymentCreateRespVO response = service.createPayment(101L, req);

        assertEquals(9101L, response.getPaymentId());
        assertEquals(9001L, response.getOrderId());
        assertEquals("C-9001", response.getOrderNo());
        assertEquals(3300L, response.getAmount());
        assertEquals(PaymentStatusEnum.INITIATED.getStatus(), response.getStatus());
        ArgumentCaptor<CommercePaymentDO> captor = ArgumentCaptor.forClass(CommercePaymentDO.class);
        verify(paymentMapper).insert(captor.capture());
        CommercePaymentDO payment = captor.getValue();
        assertEquals(9001L, payment.getOrderId());
        assertEquals(101L, payment.getMemberUserId());
        assertEquals(3300L, payment.getAmount());
        assertEquals(PaymentStatusEnum.INITIATED.getStatus(), payment.getStatus());
        assertTrue(payment.getPaymentNo().startsWith("P"));
    }

    @Test
    void createPayment_replaysExistingIntentWithoutCreatingAnotherPayment() {
        CommerceOrderDO order = pendingOrder();
        CommercePaymentDO existing = payment(PaymentStatusEnum.INITIATED.getStatus(), null);
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order);
        when(paymentMapper.selectByOrderIdForUpdate(9001L)).thenReturn(existing);

        PaymentCreateRespVO response = service.createPayment(101L,
                new PaymentCreateReqVO().setOrderId(9001L).setPaymentMethod("SIMULATED"));

        assertEquals(existing.getId(), response.getPaymentId());
        assertEquals(existing.getPaymentNo(), response.getPaymentNo());
        verify(paymentMapper, never()).insert(any(CommercePaymentDO.class));
    }

    @Test
    void createPayment_rejectsPaidOrderAndUnsupportedMethod() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(
                pendingOrder().setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus()));
        ServiceException stateError = assertThrows(ServiceException.class, () -> service.createPayment(101L,
                new PaymentCreateReqVO().setOrderId(9001L).setPaymentMethod("SIMULATED")));
        assertEquals(PAYMENT_ORDER_NOT_PAYABLE.getCode(), stateError.getCode());

        ServiceException methodError = assertThrows(ServiceException.class, () -> service.createPayment(101L,
                new PaymentCreateReqVO().setOrderId(9001L).setPaymentMethod("ALIPAY")));
        assertEquals(PAYMENT_METHOD_INVALID.getCode(), methodError.getCode());
        verify(orderMapper).selectOwnedForUpdate(101L, 9001L);
        verifyNoInteractions(paymentMapper);
    }

    @Test
    void createPayment_rejectsCanceledOrder() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(
                pendingOrder().setStatus(OrderStatusEnum.CANCELED.getStatus()));

        ServiceException error = assertThrows(ServiceException.class, () -> service.createPayment(101L,
                new PaymentCreateReqVO().setOrderId(9001L).setPaymentMethod("SIMULATED")));

        assertEquals(PAYMENT_ORDER_NOT_PAYABLE.getCode(), error.getCode());
        verifyNoInteractions(paymentMapper);
    }

    @Test
    void simulateCallback_marksPaymentAndOrderPaidWithMatchingAmount() {
        CommercePaymentDO payment = payment(PaymentStatusEnum.INITIATED.getStatus(), null);
        CommerceOrderDO order = pendingOrder();
        when(paymentMapper.selectByPaymentNo("P-20260817-001")).thenReturn(payment);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(order);
        when(paymentMapper.selectByIdForUpdate(9101L)).thenReturn(payment);
        when(paymentMapper.markSuccess(eq(9101L), eq("callback-001"), any())).thenReturn(1);
        when(orderMapper.markPaid(9001L)).thenReturn(1);

        PaymentCallbackRespVO response = service.simulateCallback(new PaymentSimulateCallbackReqVO()
                .setPaymentNo(" P-20260817-001 ").setCallbackId(" callback-001 ").setPaidAmount(3300L));

        assertEquals(9101L, response.getPaymentId());
        assertEquals(PaymentStatusEnum.SUCCESS.getStatus(), response.getPaymentStatus());
        assertEquals(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus(), response.getOrderStatus());
        assertEquals(3300L, response.getPaidAmount());
        assertEquals("callback-001", response.getCallbackId());
        verify(paymentMapper).markSuccess(eq(9101L), eq("callback-001"), any());
        verify(orderMapper).markPaid(9001L);
        verify(outboxEventAppender).append(eq(CommerceOutboxEventTypeEnum.ORDER_PAID), eq(9001L), any());
    }

    @Test
    void simulateCallback_replaysSameSuccessWithoutChangingStateAgain() {
        CommercePaymentDO payment = payment(PaymentStatusEnum.SUCCESS.getStatus(), "callback-001");
        CommerceOrderDO order = pendingOrder().setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus());
        when(paymentMapper.selectByPaymentNo("P-20260817-001")).thenReturn(payment);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(order);
        when(paymentMapper.selectByIdForUpdate(9101L)).thenReturn(payment);

        PaymentCallbackRespVO response = service.simulateCallback(new PaymentSimulateCallbackReqVO()
                .setPaymentNo("P-20260817-001").setCallbackId("callback-001").setPaidAmount(3300L));

        assertEquals(PaymentStatusEnum.SUCCESS.getStatus(), response.getPaymentStatus());
        assertEquals(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus(), response.getOrderStatus());
        verify(paymentMapper, never()).markSuccess(anyLong(), anyString(), any());
        verify(orderMapper, never()).markPaid(anyLong());
        verify(outboxEventAppender, never()).append(any(), any(), any());
    }

    @Test
    void simulateCallback_rejectsAmountMismatchBeforeStateChange() {
        CommercePaymentDO payment = payment(PaymentStatusEnum.INITIATED.getStatus(), null);
        when(paymentMapper.selectByPaymentNo("P-20260817-001")).thenReturn(payment);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(pendingOrder());
        when(paymentMapper.selectByIdForUpdate(9101L)).thenReturn(payment);

        ServiceException error = assertThrows(ServiceException.class, () -> service.simulateCallback(
                new PaymentSimulateCallbackReqVO().setPaymentNo("P-20260817-001")
                        .setCallbackId("callback-001").setPaidAmount(3299L)));

        assertEquals(PAYMENT_AMOUNT_MISMATCH.getCode(), error.getCode());
        verify(paymentMapper, never()).markSuccess(anyLong(), anyString(), any());
        verify(orderMapper, never()).markPaid(anyLong());
    }

    @Test
    void simulateCallback_rejectsDifferentCallbackForCompletedPayment() {
        CommercePaymentDO payment = payment(PaymentStatusEnum.SUCCESS.getStatus(), "callback-001");
        when(paymentMapper.selectByPaymentNo("P-20260817-001")).thenReturn(payment);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(
                pendingOrder().setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus()));
        when(paymentMapper.selectByIdForUpdate(9101L)).thenReturn(payment);

        ServiceException error = assertThrows(ServiceException.class, () -> service.simulateCallback(
                new PaymentSimulateCallbackReqVO().setPaymentNo("P-20260817-001")
                        .setCallbackId("callback-002").setPaidAmount(3300L)));

        assertEquals(PAYMENT_CALLBACK_CONFLICT.getCode(), error.getCode());
        verify(paymentMapper, never()).markSuccess(anyLong(), anyString(), any());
    }

    @Test
    void simulateCallback_rejectsCanceledPaymentWithoutStateChange() {
        CommercePaymentDO payment = payment(PaymentStatusEnum.CANCELED.getStatus(), null);
        when(paymentMapper.selectByPaymentNo("P-20260817-001")).thenReturn(payment);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(
                pendingOrder().setStatus(OrderStatusEnum.CANCELED.getStatus()));
        when(paymentMapper.selectByIdForUpdate(9101L)).thenReturn(payment);

        ServiceException error = assertThrows(ServiceException.class, () -> service.simulateCallback(
                new PaymentSimulateCallbackReqVO().setPaymentNo("P-20260817-001")
                        .setCallbackId("callback-001").setPaidAmount(3300L)));

        assertEquals(PAYMENT_CALLBACK_CONFLICT.getCode(), error.getCode());
        verify(paymentMapper, never()).markSuccess(anyLong(), anyString(), any());
        verify(orderMapper, never()).markPaid(anyLong());
    }

    @Test
    void simulateCallback_rejectsUnknownPaymentAndInvalidIdentifiers() {
        when(paymentMapper.selectByPaymentNo("P-20260817-001")).thenReturn(null);
        ServiceException missing = assertThrows(ServiceException.class, () -> service.simulateCallback(
                new PaymentSimulateCallbackReqVO().setPaymentNo("P-20260817-001")
                        .setCallbackId("callback-001").setPaidAmount(3300L)));
        assertEquals(PAYMENT_NOT_FOUND.getCode(), missing.getCode());

        ServiceException invalid = assertThrows(ServiceException.class, () -> service.simulateCallback(
                new PaymentSimulateCallbackReqVO().setPaymentNo("bad").setCallbackId("callback-001")
                        .setPaidAmount(3300L)));
        assertEquals(PAYMENT_NO_INVALID.getCode(), invalid.getCode());
    }

    private static CommerceOrderDO pendingOrder() {
        return new CommerceOrderDO().setId(9001L).setOrderNo("C-9001").setMemberUserId(101L)
                .setPayableAmount(3300L).setStatus(OrderStatusEnum.PENDING_PAYMENT.getStatus());
    }

    private static CommercePaymentDO payment(Integer status, String callbackId) {
        return new CommercePaymentDO().setId(9101L).setPaymentNo("P-20260817-001").setOrderId(9001L)
                .setOrderNo("C-9001").setMemberUserId(101L).setAmount(3300L).setStatus(status)
                .setCallbackId(callbackId);
    }
}
