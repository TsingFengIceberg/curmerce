package cn.iocoder.yudao.module.commerce.service.refund;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundApplyReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.refund.CommerceRefundMapper;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
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
    @InjectMocks private RefundServiceImpl service;

    @Test
    void applyRefund_createsSnapshotAndCompletesSimulatedRefund() {
        CommerceOrderDO order = order(OrderStatusEnum.SHIPPED.getStatus()).setPayableAmount(3300L);
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order);
        when(refundMapper.selectByOrderIdForUpdate(9001L)).thenReturn(null);
        when(refundMapper.insert(any(CommerceRefundDO.class))).thenAnswer(invocation -> {
            invocation.<CommerceRefundDO>getArgument(0).setId(9201L);
            return 1;
        });
        when(refundMapper.markSuccess(eq(9201L), any())).thenReturn(1);

        var response = service.applyRefund(101L, new RefundApplyReqVO()
                .setOrderId(9001L).setReason("商品不需要了"));

        assertEquals(9201L, response.getId());
        assertEquals(3300L, response.getAmount());
        assertEquals(RefundStatusEnum.SUCCESS.getStatus(), response.getStatus());
        ArgumentCaptor<CommerceRefundDO> captor = ArgumentCaptor.forClass(CommerceRefundDO.class);
        verify(refundMapper).insert(captor.capture());
        assertEquals(9001L, captor.getValue().getOrderId());
        assertEquals(3300L, captor.getValue().getAmount());
        verify(refundMapper).markSuccess(eq(9201L), any());
    }

    @Test
    void applyRefund_rejectsForeignOrMissingOrder() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.applyRefund(101L,
                new RefundApplyReqVO().setOrderId(9001L).setReason("退款")));

        assertEquals(ORDER_NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(refundMapper);
    }

    @Test
    void applyRefund_rejectsUnpaidOrder() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order(OrderStatusEnum.PENDING_PAYMENT.getStatus()));

        ServiceException error = assertThrows(ServiceException.class, () -> service.applyRefund(101L,
                new RefundApplyReqVO().setOrderId(9001L).setReason("退款")));

        assertEquals(REFUND_ORDER_NOT_REFUNDABLE.getCode(), error.getCode());
        verifyNoInteractions(refundMapper);
    }

    @Test
    void applyRefund_returnsExistingRecordWithoutCreatingAnother() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order(OrderStatusEnum.COMPLETED.getStatus()));
        CommerceRefundDO existing = new CommerceRefundDO().setId(9201L).setRefundNo("R-1")
                .setOrderId(9001L).setOrderNo("C-1").setMemberUserId(101L).setAmount(3300L)
                .setStatus(RefundStatusEnum.SUCCESS.getStatus()).setReason("原申请");
        when(refundMapper.selectByOrderIdForUpdate(9001L)).thenReturn(existing);

        var response = service.applyRefund(101L, new RefundApplyReqVO()
                .setOrderId(9001L).setReason("重复申请"));

        assertEquals(9201L, response.getId());
        assertEquals("原申请", response.getReason());
        verify(refundMapper, never()).insert(any(CommerceRefundDO.class));
        verify(refundMapper, never()).markSuccess(anyLong(), any());
    }

    private static CommerceOrderDO order(Integer status) {
        return new CommerceOrderDO().setId(9001L).setOrderNo("C-1").setMemberUserId(101L)
                .setStatus(status).setPayableAmount(3300L);
    }
}
