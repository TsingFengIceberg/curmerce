package cn.iocoder.yudao.module.commerce.service.refund;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundAuditReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundCallbackReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundApplyReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundPageReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.refund.CommerceRefundMapper;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxEventTypeEnum;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxEventAppender;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock private MemberUserApi memberUserApi;
    @Mock private CommerceOrderMapper orderMapper;
    @Mock private CommerceRefundMapper refundMapper;
    @Mock private MerchantAccessService merchantAccessService;
    @Mock private CommerceOutboxEventAppender outboxEventAppender;
    @InjectMocks private RefundServiceImpl service;

    @Test
    void applyRefund_staysRequestedAndSynchronizesOrderAfterSaleStatus() {
        CommerceOrderDO order = order(OrderStatusEnum.SHIPPED.getStatus()).setPayableAmount(3300L);
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order);
        when(refundMapper.selectByOrderIdForUpdate(9001L)).thenReturn(null);
        when(refundMapper.insert(any(CommerceRefundDO.class))).thenAnswer(invocation -> {
            invocation.<CommerceRefundDO>getArgument(0).setId(9201L);
            return 1;
        });
        when(orderMapper.markRefundStatus(9001L, RefundStatusEnum.REQUESTED.getStatus())).thenReturn(1);

        var response = service.applyRefund(101L, new RefundApplyReqVO()
                .setOrderId(9001L).setReason("商品不需要了"));

        assertEquals(9201L, response.getId());
        assertEquals(3300L, response.getAmount());
        assertEquals(RefundStatusEnum.REQUESTED.getStatus(), response.getStatus());
        ArgumentCaptor<CommerceRefundDO> captor = ArgumentCaptor.forClass(CommerceRefundDO.class);
        verify(refundMapper).insert(captor.capture());
        assertEquals(9001L, captor.getValue().getOrderId());
        verify(orderMapper).markRefundStatus(9001L, RefundStatusEnum.REQUESTED.getStatus());
        verify(refundMapper, never()).markCallback(anyLong(), anyString(), anyBoolean(), any());
    }

    @Test
    void applyRefund_rejectsForeignOrUnpaidOrder() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(null);
        ServiceException foreign = assertThrows(ServiceException.class, () -> service.applyRefund(101L,
                new RefundApplyReqVO().setOrderId(9001L).setReason("退款")));
        assertEquals(ORDER_NOT_FOUND.getCode(), foreign.getCode());

        when(orderMapper.selectOwnedForUpdate(101L, 9002L)).thenReturn(order(OrderStatusEnum.PENDING_PAYMENT.getStatus()));
        ServiceException unpaid = assertThrows(ServiceException.class, () -> service.applyRefund(101L,
                new RefundApplyReqVO().setOrderId(9002L).setReason("退款")));
        assertEquals(REFUND_ORDER_NOT_REFUNDABLE.getCode(), unpaid.getCode());
    }

    @Test
    void applyRefund_replaysExistingRecordWithoutCreatingAnother() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order(OrderStatusEnum.COMPLETED.getStatus()));
        CommerceRefundDO existing = refund(9201L, RefundStatusEnum.SUCCESS.getStatus());
        when(refundMapper.selectByOrderIdForUpdate(9001L)).thenReturn(existing);

        var response = service.applyRefund(101L, new RefundApplyReqVO()
                .setOrderId(9001L).setReason("重复申请"));

        assertEquals(9201L, response.getId());
        verify(refundMapper, never()).insert(any(CommerceRefundDO.class));
    }

    @Test
    void approveRefund_movesRequestedToApproved() {
        CommerceRefundDO refund = refund(9201L, RefundStatusEnum.REQUESTED.getStatus());
        when(refundMapper.selectByIdForUpdate(9201L)).thenReturn(refund);
        when(refundMapper.markApproved(eq(9201L), eq(7001L), any(), eq("符合规则"))).thenReturn(1);
        when(orderMapper.markRefundStatus(9001L, RefundStatusEnum.APPROVED.getStatus())).thenReturn(1);

        service.approveRefund(7001L, new RefundAuditReqVO().setId(9201L).setRemark("符合规则"));

        verify(refundMapper).markApproved(eq(9201L), eq(7001L), any(), eq("符合规则"));
        verify(orderMapper).markRefundStatus(9001L, RefundStatusEnum.APPROVED.getStatus());
    }

    @Test
    void rejectRefund_requiresRemarkAndMovesToRejected() {
        CommerceRefundDO refund = refund(9201L, RefundStatusEnum.REQUESTED.getStatus());
        when(refundMapper.selectByIdForUpdate(9201L)).thenReturn(refund);
        ServiceException missingRemark = assertThrows(ServiceException.class,
                () -> service.rejectRefund(7001L, new RefundAuditReqVO().setId(9201L)));
        assertEquals(REFUND_REVIEW_REMARK_INVALID.getCode(), missingRemark.getCode());
        verifyNoInteractions(refundMapper);

        when(refundMapper.markRejected(eq(9201L), eq(7001L), any(), eq("凭证不足"))).thenReturn(1);
        when(orderMapper.markRefundStatus(9001L, RefundStatusEnum.REJECTED.getStatus())).thenReturn(1);
        service.rejectRefund(7001L, new RefundAuditReqVO().setId(9201L).setRemark("凭证不足"));
        verify(refundMapper).markRejected(eq(9201L), eq(7001L), any(), eq("凭证不足"));
    }

    @Test
    void simulateCallback_supportsSuccessAndSameCallbackIsIdempotent() {
        CommerceRefundDO refund = refund(9201L, RefundStatusEnum.APPROVED.getStatus());
        when(refundMapper.selectByRefundNoForUpdate("R-1")).thenReturn(refund);
        when(refundMapper.markCallback(eq(9201L), eq("cb-1"), eq(true), any())).thenReturn(1);
        when(orderMapper.markRefundStatus(9001L, RefundStatusEnum.SUCCESS.getStatus())).thenReturn(1);

        var response = service.simulateCallback(new RefundCallbackReqVO()
                .setRefundNo("R-1").setCallbackId("cb-1").setSuccess(true));
        assertEquals(RefundStatusEnum.SUCCESS.getStatus(), response.getStatus());

        CommerceRefundDO completed = refund(9201L, RefundStatusEnum.SUCCESS.getStatus())
                .setRefundNo("R-1").setCallbackId("cb-1").setCallbackSuccess(true);
        when(refundMapper.selectByRefundNoForUpdate("R-1")).thenReturn(completed);
        var replay = service.simulateCallback(new RefundCallbackReqVO()
                .setRefundNo("R-1").setCallbackId("cb-1").setSuccess(true));
        assertEquals(RefundStatusEnum.SUCCESS.getStatus(), replay.getStatus());
        verify(refundMapper, times(1)).markCallback(eq(9201L), eq("cb-1"), eq(true), any());
        verify(outboxEventAppender).append(eq(CommerceOutboxEventTypeEnum.REFUND_SUCCESS), eq(9201L), any());
    }

    @Test
    void simulateCallback_failureAppendsRefundFailedEvent() {
        CommerceRefundDO refund = refund(9201L, RefundStatusEnum.APPROVED.getStatus());
        when(refundMapper.selectByRefundNoForUpdate("R-1")).thenReturn(refund);
        when(refundMapper.markCallback(eq(9201L), eq("cb-2"), eq(false), any())).thenReturn(1);
        when(orderMapper.markRefundStatus(9001L, RefundStatusEnum.FAILED.getStatus())).thenReturn(1);

        var response = service.simulateCallback(new RefundCallbackReqVO()
                .setRefundNo("R-1").setCallbackId("cb-2").setSuccess(false));

        assertEquals(RefundStatusEnum.FAILED.getStatus(), response.getStatus());
        verify(refundMapper).markCallback(eq(9201L), eq("cb-2"), eq(false), any());
        verify(orderMapper).markRefundStatus(9001L, RefundStatusEnum.FAILED.getStatus());
        verify(outboxEventAppender).append(eq(CommerceOutboxEventTypeEnum.REFUND_FAILED), eq(9201L), any());
    }

    @Test
    void simulateCallback_rejectsNotApprovedAndConflictingReplay() {
        when(refundMapper.selectByRefundNoForUpdate("R-1"))
                .thenReturn(refund(9201L, RefundStatusEnum.REQUESTED.getStatus()));
        ServiceException notApproved = assertThrows(ServiceException.class, () -> service.simulateCallback(
                new RefundCallbackReqVO().setRefundNo("R-1").setCallbackId("cb-1").setSuccess(true)));
        assertEquals(REFUND_STATE_INVALID.getCode(), notApproved.getCode());

        when(refundMapper.selectByRefundNoForUpdate("R-2"))
                .thenReturn(refund(9202L, RefundStatusEnum.FAILED.getStatus()).setRefundNo("R-2")
                        .setCallbackId("cb-2").setCallbackSuccess(false));
        ServiceException conflict = assertThrows(ServiceException.class, () -> service.simulateCallback(
                new RefundCallbackReqVO().setRefundNo("R-2").setCallbackId("cb-2").setSuccess(true)));
        assertEquals(REFUND_CALLBACK_CONFLICT.getCode(), conflict.getCode());
    }

    @Test
    void merchantApprovalRequiresCurrentMerchantOrderOwnership() {
        MerchantAccessContext access = new MerchantAccessContext(new MerchantDO().setId(401L),
                new StoreDO().setId(501L).setMerchantId(401L));
        when(merchantAccessService.requireApprovedOwner()).thenReturn(access);
        when(refundMapper.selectByIdForUpdate(9201L)).thenReturn(refund(9201L, RefundStatusEnum.REQUESTED.getStatus()));
        when(orderMapper.selectById(9001L)).thenReturn(order(OrderStatusEnum.SHIPPED.getStatus())
                .setMerchantId(999L).setStoreId(999L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.approveMerchantRefund(7001L,
                new RefundAuditReqVO().setId(9201L).setRemark("商家审核")));
        assertEquals(REFUND_NOT_FOUND.getCode(), error.getCode());
        verify(refundMapper, never()).markApproved(anyLong(), any(), any(), any());
    }

    @Test
    void merchantApprovalPersistsCurrentReviewerId() {
        MerchantAccessContext access = new MerchantAccessContext(new MerchantDO().setId(401L),
                new StoreDO().setId(501L).setMerchantId(401L));
        when(merchantAccessService.requireApprovedOwner()).thenReturn(access);
        when(refundMapper.selectByIdForUpdate(9201L)).thenReturn(refund(9201L,
                RefundStatusEnum.REQUESTED.getStatus()));
        when(orderMapper.selectById(9001L)).thenReturn(order(OrderStatusEnum.SHIPPED.getStatus()));
        when(refundMapper.markApproved(eq(9201L), eq(7001L), any(), eq("商家审核"))).thenReturn(1);
        when(orderMapper.markRefundStatus(9001L, RefundStatusEnum.APPROVED.getStatus())).thenReturn(1);

        service.approveMerchantRefund(7001L, new RefundAuditReqVO().setId(9201L).setRemark("商家审核"));

        verify(refundMapper).markApproved(eq(9201L), eq(7001L), any(), eq("商家审核"));
        verify(orderMapper).markRefundStatus(9001L, RefundStatusEnum.APPROVED.getStatus());
    }

    @Test
    void getRefundPage_usesBuyerOwnershipAndStatusFilters() {
        CommerceRefundDO refund = refund(9201L, RefundStatusEnum.REQUESTED.getStatus());
        when(refundMapper.selectPageOwned(eq(101L), any(RefundPageReqVO.class)))
                .thenReturn(new PageResult<>(java.util.List.of(refund), 1L));

        PageResult<?> page = service.getRefundPage(101L, new RefundPageReqVO()
                .setStatus(RefundStatusEnum.REQUESTED.getStatus()).setOrderNo("C-1"));

        assertEquals(1L, page.getTotal());
        assertEquals(9201L, ((cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundRespVO)
                page.getList().get(0)).getId());
    }

    private static CommerceOrderDO order(Integer status) {
        return new CommerceOrderDO().setId(9001L).setOrderNo("C-1").setMemberUserId(101L)
                .setMerchantId(401L).setStoreId(501L).setStatus(status).setPayableAmount(3300L);
    }

    private static CommerceRefundDO refund(Long id, Integer status) {
        return new CommerceRefundDO().setId(id).setRefundNo("R-1").setOrderId(9001L).setOrderNo("C-1")
                .setMemberUserId(101L).setAmount(3300L).setStatus(status).setReason("退款");
    }
}
