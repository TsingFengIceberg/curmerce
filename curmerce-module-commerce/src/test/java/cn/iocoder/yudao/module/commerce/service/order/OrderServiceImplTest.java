package cn.iocoder.yudao.module.commerce.service.order;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderShipReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.CommerceOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.OrderCreateRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.OrderDetailRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.OrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.OrderSummaryRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.PersonalSellerOrderRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.cart.CartItemDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderItemDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.cart.CartItemMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderItemMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductCategoryMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.payment.CommercePaymentMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.refund.CommerceRefundMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseItemMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleasePurchaseMapper;
import cn.iocoder.yudao.module.commerce.dal.dataobject.payment.CommercePaymentDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.payment.PaymentStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxEventTypeEnum;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxEventAppender;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private MemberUserApi memberUserApi;
    @Mock private MemberAddressApi memberAddressApi;
    @Mock private CartItemMapper cartItemMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductSkuMapper productSkuMapper;
    @Mock private ProductCategoryMapper productCategoryMapper;
    @Mock private MerchantMapper merchantMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private CommerceOrderMapper orderMapper;
    @Mock private CommerceOrderItemMapper orderItemMapper;
    @Mock private MerchantAccessService merchantAccessService;
    @Mock private CommercePaymentMapper paymentMapper;
    @Mock private CommerceRefundMapper refundMapper;
    @Mock private CommerceReleaseItemMapper releaseItemMapper;
    @Mock private CommerceReleasePurchaseMapper releasePurchaseMapper;
    @Mock private CommerceOutboxEventAppender outboxEventAppender;
    @InjectMocks private OrderServiceImpl service;

    @Test
    void createOrder_persistsSnapshotsDeductsStockAndDeletesOnlyPurchasedRows() {
        long userId = 101L;
        CartItemDO purchased = cartItem(11L, userId, 201L, 301L, 2, true);
        CartItemDO unselected = cartItem(12L, userId, 202L, 302L, 1, false);
        ProductDO product = product(201L, 401L, 501L, "Tea");
        ProductSkuDO sku = sku(301L, 201L, 401L, "tea-red", 1250L, 8);
        prepareSellable(userId, purchased, product, sku);
        when(cartItemMapper.selectSelectedListByUserIdForUpdate(userId)).thenReturn(List.of(purchased));
        when(orderMapper.insert(any(CommerceOrderDO.class))).thenAnswer(invocation -> {
            invocation.<CommerceOrderDO>getArgument(0).setId(9001L);
            return 1;
        });
        when(productSkuMapper.deductStock(301L, 2)).thenReturn(1);

        OrderCreateRespVO response = service.createOrder(userId, 701L, "checkout-101");

        assertEquals(9001L, response.getOrderId());
        assertEquals(OrderStatusEnum.PENDING_PAYMENT.getStatus(), response.getStatus());
        assertEquals(2500L, response.getPayableAmount());
        ArgumentCaptor<CommerceOrderDO> orderCaptor = ArgumentCaptor.forClass(CommerceOrderDO.class);
        verify(orderMapper).insert(orderCaptor.capture());
        CommerceOrderDO order = orderCaptor.getValue();
        assertEquals(userId, order.getMemberUserId());
        assertEquals(401L, order.getMerchantId());
        assertEquals(501L, order.getStoreId());
        assertEquals("Alice", order.getReceiverName());
        assertEquals("Shanghai Pudong", order.getReceiverAreaName());
        assertEquals(2500L, order.getTotalAmount());
        verify(orderItemMapper).insert((CommerceOrderItemDO) argThat((CommerceOrderItemDO item) -> item.getOrderId().equals(9001L)
                && item.getProductId().equals(201L)
                && item.getSkuId().equals(301L)
                && item.getPrice().equals(1250L)
                && item.getQuantity().equals(2)
                && item.getTotalAmount().equals(2500L)));
        verify(productSkuMapper).deductStock(301L, 2);
        verify(cartItemMapper).deleteOwned(userId, List.of(11L));
        verify(cartItemMapper, never()).deleteOwned(userId, List.of(12L));
        assertNotNull(unselected);
    }

    @Test
    void createOrder_sameBuyerAndKeyReturnsCommittedOrderWithoutTouchingCartOrStock() {
        CommerceOrderDO existing = new CommerceOrderDO().setId(9002L).setOrderNo("C-existing")
                .setStatus(OrderStatusEnum.PENDING_PAYMENT.getStatus()).setPayableAmount(3300L);
        when(orderMapper.selectByUserAndIdempotencyKey(101L, "checkout-101")).thenReturn(existing);

        OrderCreateRespVO response = service.createOrder(101L, 701L, " checkout-101 ");

        assertEquals(9002L, response.getOrderId());
        assertEquals("C-existing", response.getOrderNo());
        assertEquals(3300L, response.getPayableAmount());
        verify(memberUserApi).validateActiveUserForUpdate(101L);
        verifyNoInteractions(memberAddressApi, cartItemMapper, productMapper, productSkuMapper,
                productCategoryMapper, merchantMapper, storeMapper, orderItemMapper);
        verify(orderMapper).selectByUserAndIdempotencyKey(101L, "checkout-101");
    }

    @Test
    void createOrder_rejectsEmptySelectionBeforeAnyOrderWrite() {
        when(cartItemMapper.selectSelectedListByUserIdForUpdate(101L)).thenReturn(List.of());
        when(memberAddressApi.getOwnedAddressForUpdate(101L, 701L)).thenReturn(address(701L, 101L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(101L, 701L, "checkout-101"));

        assertEquals(ORDER_CHECKOUT_EMPTY.getCode(), error.getCode());
        verifyNoInteractions(orderItemMapper);
        verify(orderMapper, never()).insert((CommerceOrderDO) any());
    }

    @Test
    void createOrder_rejectsMixedStoresWithoutChangingStock() {
        CartItemDO first = cartItem(11L, 101L, 201L, 301L, 1, true);
        CartItemDO second = cartItem(12L, 101L, 202L, 302L, 1, true);
        ProductDO firstProduct = product(201L, 401L, 501L, "Tea");
        ProductDO secondProduct = product(202L, 401L, 502L, "Cup");
        prepareSellable(101L, first, firstProduct, sku(301L, 201L, 401L, "tea", 1000L, 1));
        when(cartItemMapper.selectSelectedListByUserIdForUpdate(101L)).thenReturn(List.of(first, second));
        when(productMapper.selectByIdForUpdate(202L)).thenReturn(secondProduct);
        when(productSkuMapper.selectByIdAndProductIdForUpdate(302L, 202L))
                .thenReturn(sku(302L, 202L, 401L, "cup", 1200L, 1));
        when(merchantMapper.selectById(401L)).thenReturn(merchant(401L));
        when(storeMapper.selectById(502L)).thenReturn(store(502L, 401L));
        when(productCategoryMapper.selectById(601L)).thenReturn(category(601L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(101L, 701L, "checkout-101"));

        assertEquals(ORDER_CHECKOUT_MULTI_STORE.getCode(), error.getCode());
        verify(productSkuMapper, never()).deductStock(anyLong(), anyInt());
        verify(orderMapper, never()).insert((CommerceOrderDO) any());
        verify(cartItemMapper, never()).deleteOwned(anyLong(), anyCollection());
    }

    @Test
    void createOrder_rejectsPersonalSellerBuyingOwnListing() {
        CartItemDO cart = cartItem(11L, 101L, 201L, 301L, 1, true);
        ProductDO product = new ProductDO().setId(201L).setSellerType(2).setSellerUserId(101L)
                .setCategoryId(601L).setAuditStatus(2).setSaleStatus(1).setName("旧相机");
        ProductSkuDO sku = new ProductSkuDO().setId(301L).setProductId(201L).setMerchantId(null)
                .setPrice(1000L).setStock(1).setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(memberAddressApi.getOwnedAddressForUpdate(101L, 701L)).thenReturn(address(701L, 101L));
        when(cartItemMapper.selectSelectedListByUserIdForUpdate(101L)).thenReturn(List.of(cart));
        when(productMapper.selectByIdForUpdate(201L)).thenReturn(product);
        when(productSkuMapper.selectByIdAndProductIdForUpdate(301L, 201L)).thenReturn(sku);
        when(productCategoryMapper.selectById(601L)).thenReturn(category(601L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(101L, 701L, "checkout-personal"));

        assertEquals(PERSONAL_LISTING_SELF_PURCHASE.getCode(), error.getCode());
        verify(productSkuMapper, never()).deductStock(anyLong(), anyInt());
        verify(orderMapper, never()).insert((CommerceOrderDO) any());
    }

    @Test
    void createOrder_stockFailureDoesNotInsertItemsOrDeleteCart() {
        CartItemDO cart = cartItem(11L, 101L, 201L, 301L, 2, true);
        prepareSellable(101L, cart, product(201L, 401L, 501L, "Tea"),
                sku(301L, 201L, 401L, "tea", 1000L, 1));
        when(cartItemMapper.selectSelectedListByUserIdForUpdate(101L)).thenReturn(List.of(cart));
        when(productSkuMapper.deductStock(301L, 2)).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(101L, 701L, "checkout-101"));

        assertEquals(ORDER_STOCK_INSUFFICIENT.getCode(), error.getCode());
        verify(orderMapper).insert(any(CommerceOrderDO.class));
        verify(orderItemMapper, never()).insert((CommerceOrderItemDO) any());
        verify(cartItemMapper, never()).deleteOwned(anyLong(), anyCollection());
    }

    @Test
    void createOrder_rejectsForeignOrMissingAddressBeforeCartLock() {
        when(memberAddressApi.getOwnedAddressForUpdate(101L, 701L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(101L, 701L, "checkout-101"));

        assertEquals(ORDER_ADDRESS_NOT_AVAILABLE.getCode(), error.getCode());
        verifyNoInteractions(cartItemMapper, productMapper, productSkuMapper, orderItemMapper);
        verify(orderMapper).selectByUserAndIdempotencyKey(101L, "checkout-101");
    }

    @Test
    void createOrder_rejectsInvalidIdempotencyKeyBeforeMemberLookup() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(101L, 701L, "short"));

        assertEquals(ORDER_IDEMPOTENCY_KEY_INVALID.getCode(), error.getCode());
        verifyNoInteractions(memberUserApi, memberAddressApi, orderMapper);
    }

    @Test
    void getOrder_readsOnlyPersistedSnapshotsAndScopesByBuyer() {
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setOrderNo("C-1")
                .setMemberUserId(101L).setMerchantId(401L).setStoreId(501L)
                .setStatus(OrderStatusEnum.PENDING_PAYMENT.getStatus()).setItemCount(1)
                .setTotalAmount(1250L).setPayableAmount(1250L)
                .setReceiverName("Alice").setReceiverMobile("13800138000")
                .setReceiverAreaId(310115).setReceiverAreaName("Shanghai Pudong")
                .setReceiverDetailAddress("Old address");
        CommerceOrderItemDO item = new CommerceOrderItemDO().setId(9101L).setOrderId(9001L)
                .setProductId(201L).setSkuId(301L).setProductName("Snapshot tea")
                .setProductImageUrl("old.png").setSkuCode("tea").setPrice(1250L)
                .setQuantity(1).setTotalAmount(1250L);
        when(orderMapper.selectOwned(101L, 9001L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(9001L)).thenReturn(List.of(item));

        OrderDetailRespVO response = service.getOrder(101L, 9001L);

        assertEquals("Snapshot tea", response.getItems().get(0).getProductName());
        assertEquals("Old address", response.getReceiverDetailAddress());
        verify(orderMapper).selectOwned(101L, 9001L);
        verify(orderItemMapper).selectListByOrderId(9001L);
        verifyNoInteractions(productMapper, productSkuMapper, memberAddressApi);
    }

    @Test
    void getOrderPage_scopesByBuyerAndForwardsStatusFilter() {
        OrderPageReqVO request = new OrderPageReqVO().setStatus(OrderStatusEnum.SHIPPED.getStatus());
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setOrderNo("C-1")
                .setMemberUserId(101L).setStatus(OrderStatusEnum.SHIPPED.getStatus())
                .setRefundStatus(RefundStatusEnum.NONE.getStatus()).setItemCount(1)
                .setTotalAmount(1250L).setPayableAmount(1250L);
        when(orderMapper.selectPageOwned(101L, request)).thenReturn(new PageResult<>(List.of(order), 1L));

        PageResult<OrderSummaryRespVO> response = service.getOrderPage(101L, request);

        assertEquals(1L, response.getTotal());
        assertEquals(OrderStatusEnum.SHIPPED.getStatus(), response.getList().get(0).getStatus());
        assertEquals(RefundStatusEnum.NONE.getStatus(), response.getList().get(0).getRefundStatus());
        verify(orderMapper).selectPageOwned(101L, request);
    }

    @Test
    void getOrder_includesPaymentAndRefundSummaries() {
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setOrderNo("C-1")
                .setMemberUserId(101L).setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus())
                .setRefundStatus(RefundStatusEnum.APPROVED.getStatus()).setItemCount(1)
                .setTotalAmount(1250L).setPayableAmount(1250L);
        CommercePaymentDO payment = new CommercePaymentDO().setPaymentNo("P-1")
                .setAmount(1250L).setStatus(PaymentStatusEnum.SUCCESS.getStatus())
                .setPaidTime(LocalDateTime.of(2026, 8, 18, 10, 0));
        CommerceRefundDO refund = new CommerceRefundDO().setId(9201L).setRefundNo("R-1")
                .setOrderId(9001L).setAmount(1250L).setStatus(RefundStatusEnum.APPROVED.getStatus())
                .setReason("商品问题");
        when(orderMapper.selectOwned(101L, 9001L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(9001L)).thenReturn(List.of());
        when(paymentMapper.selectByOrderId(9001L)).thenReturn(payment);
        when(refundMapper.selectByOrderId(9001L)).thenReturn(refund);

        OrderDetailRespVO response = service.getOrder(101L, 9001L);

        assertEquals("P-1", response.getPaymentNo());
        assertEquals(PaymentStatusEnum.SUCCESS.getStatus(), response.getPaymentStatus());
        assertEquals(1250L, response.getPaymentAmount());
        assertEquals(payment.getPaidTime(), response.getPaidTime());
        assertEquals(RefundStatusEnum.APPROVED.getStatus(), response.getRefundStatus());
        assertEquals("R-1", response.getRefund().getRefundNo());
    }

    @Test
    void cancelOrder_restoresSnapshotStockAndCancelsInitiatedPayment() {
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setMemberUserId(101L)
                .setStatus(OrderStatusEnum.PENDING_PAYMENT.getStatus());
        CommerceOrderItemDO item = new CommerceOrderItemDO().setOrderId(9001L).setSkuId(301L).setQuantity(2);
        CommercePaymentDO payment = new CommercePaymentDO().setId(9301L)
                .setStatus(cn.iocoder.yudao.module.commerce.enums.payment.PaymentStatusEnum.INITIATED.getStatus());
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(9001L)).thenReturn(List.of(item));
        when(productSkuMapper.restoreStock(301L, 2)).thenReturn(1);
        when(paymentMapper.selectByOrderIdForUpdate(9001L)).thenReturn(payment);
        when(paymentMapper.markCanceled(9301L)).thenReturn(1);
        when(orderMapper.markCanceled(101L, 9001L)).thenReturn(1);

        service.cancelOrder(101L, 9001L);

        verify(productSkuMapper).restoreStock(301L, 2);
        verify(paymentMapper).markCanceled(9301L);
        verify(orderMapper).markCanceled(101L, 9001L);
        verify(outboxEventAppender).append(eq(CommerceOutboxEventTypeEnum.ORDER_CANCELED), eq(9001L), any());
    }

    @Test
    void cancelOrder_rejectsPaidOrderWithoutRestoringStock() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(new CommerceOrderDO()
                .setId(9001L).setMemberUserId(101L)
                .setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus()));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.cancelOrder(101L, 9001L));

        assertEquals(ORDER_CANCEL_STATE_INVALID.getCode(), error.getCode());
        verifyNoInteractions(orderItemMapper, productSkuMapper, paymentMapper);
        verify(orderMapper, never()).markCanceled(anyLong(), anyLong());
    }

    @Test
    void cancelOrder_rejectsShippedCompletedAndCanceledOrders() {
        for (Integer status : List.of(OrderStatusEnum.SHIPPED.getStatus(),
                OrderStatusEnum.COMPLETED.getStatus(), OrderStatusEnum.CANCELED.getStatus())) {
            reset(orderMapper);
            when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(new CommerceOrderDO()
                    .setId(9001L).setMemberUserId(101L).setStatus(status));

            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.cancelOrder(101L, 9001L));

            assertEquals(ORDER_CANCEL_STATE_INVALID.getCode(), error.getCode());
            verify(orderMapper, never()).markCanceled(anyLong(), anyLong());
            verifyNoInteractions(orderItemMapper, productSkuMapper, paymentMapper);
        }
    }

    @Test
    void closeExpiredPendingPaymentOrders_closesOrderAndRestoresStock() {
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L)
                .setStatus(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .setPaymentDeadline(LocalDateTime.now().minusMinutes(1));
        CommerceOrderItemDO item = new CommerceOrderItemDO().setOrderId(9001L).setSkuId(301L).setQuantity(1);
        when(orderMapper.selectExpiredPendingPaymentForUpdate(any(), eq(10))).thenReturn(List.of(order));
        when(orderItemMapper.selectListByOrderId(9001L)).thenReturn(List.of(item));
        when(productSkuMapper.restoreStock(301L, 1)).thenReturn(1);
        when(paymentMapper.selectByOrderIdForUpdate(9001L)).thenReturn(null);
        when(orderMapper.markCanceled(9001L)).thenReturn(1);

        assertEquals(1, service.closeExpiredPendingPaymentOrders(LocalDateTime.now(), 10));
        verify(productSkuMapper).restoreStock(301L, 1);
        verify(orderMapper).markCanceled(9001L);
        verify(outboxEventAppender).append(eq(CommerceOutboxEventTypeEnum.ORDER_CANCELED), eq(9001L), any());
    }

    @Test
    void confirmReceipt_movesShippedOrderToCompletedWithBuyerAndStateGuard() {
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setMemberUserId(101L)
                .setStatus(OrderStatusEnum.SHIPPED.getStatus());
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(order);
        when(orderMapper.markCompleted(eq(101L), eq(9001L), any())).thenReturn(1);

        service.confirmReceipt(101L, 9001L);

        verify(memberUserApi).validateActiveUserForUpdate(101L);
        verify(orderMapper).selectOwnedForUpdate(101L, 9001L);
        verify(orderMapper).markCompleted(eq(101L), eq(9001L), any());
        verify(outboxEventAppender).append(eq(CommerceOutboxEventTypeEnum.ORDER_COMPLETED), eq(9001L), any());
    }

    @Test
    void confirmReceipt_rejectsForeignOrder() {
        when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.confirmReceipt(101L, 9001L));

        assertEquals(ORDER_NOT_FOUND.getCode(), error.getCode());
        verify(orderMapper, never()).markCompleted(anyLong(), anyLong(), any());
    }

    @Test
    void confirmReceipt_rejectsUnshippedAndAlreadyCompletedOrders() {
        for (Integer status : List.of(OrderStatusEnum.PENDING_PAYMENT.getStatus(),
                OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus(), OrderStatusEnum.COMPLETED.getStatus())) {
            reset(orderMapper);
            when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(new CommerceOrderDO()
                    .setId(9001L).setMemberUserId(101L).setStatus(status));

            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.confirmReceipt(101L, 9001L));

            assertEquals(ORDER_RECEIPT_STATE_INVALID.getCode(), error.getCode());
            verify(orderMapper, never()).markCompleted(anyLong(), anyLong(), any());
        }
    }

    @Test
    void getOrder_missingOrForeignOrderIsNeutral() {
        when(orderMapper.selectOwned(202L, 9001L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getOrder(202L, 9001L));

        assertEquals(ORDER_NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(orderItemMapper, productMapper, memberAddressApi);
    }

    @Test
    void getOwnPendingShipmentPage_scopesByMerchantAndStoreAndReturnsSnapshotsAndBuyer() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(accessContext(401L, 501L));
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setOrderNo("C-1")
                .setMemberUserId(101L).setMerchantId(401L).setStoreId(501L)
                .setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus()).setItemCount(1)
                .setTotalAmount(1250L).setPayableAmount(1250L)
                .setReceiverName("Alice").setReceiverMobile("13800138000")
                .setReceiverAreaId(310115).setReceiverAreaName("Shanghai Pudong")
                .setReceiverDetailAddress("Snapshot address");
        CommerceOrderItemDO item = new CommerceOrderItemDO().setId(9101L).setOrderId(9001L)
                .setProductId(201L).setSkuId(301L).setProductName("Snapshot tea")
                .setPrice(1250L).setQuantity(1).setTotalAmount(1250L);
        when(orderMapper.selectPagePendingShipment(any(MerchantOrderPageReqVO.class), eq(401L), eq(501L)))
                .thenReturn(new PageResult<>(List.of(order), 1L));
        when(orderItemMapper.selectListByOrderId(9001L)).thenReturn(List.of(item));
        when(memberUserApi.getUser(101L)).thenReturn(new MemberUserRespDTO().setId(101L)
                .setMobile("13900000000").setNickname("Alice buyer").setEmail("alice@example.com"));

        MerchantOrderPageReqVO request = new MerchantOrderPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);
        PageResult<MerchantOrderRespVO> result = service.getOwnPendingShipmentPage(request);

        assertEquals(1L, result.getTotal());
        MerchantOrderRespVO response = result.getList().get(0);
        assertEquals(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus(), response.getStatus());
        assertEquals("Snapshot address", response.getReceiverDetailAddress());
        assertEquals(1250L, response.getPayableAmount());
        assertEquals("Alice buyer", response.getBuyerNickname());
        assertEquals("Snapshot tea", response.getItems().get(0).getProductName());
        verify(orderMapper).selectPagePendingShipment(request, 401L, 501L);
        verifyNoInteractions(productMapper, productSkuMapper, memberAddressApi);
    }

    @Test
    void getAdminOrderPage_forwardsFiltersAndReturnsBuyerAndItemSnapshots() {
        CommerceOrderPageReqVO request = new CommerceOrderPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);
        request.setStatus(OrderStatusEnum.SHIPPED.getStatus());
        request.setOrderNo("C-1");
        request.setMerchantId(401L);
        request.setMemberUserId(101L);
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setOrderNo("C-1")
                .setMemberUserId(101L).setMerchantId(401L).setStoreId(501L)
                .setStatus(OrderStatusEnum.SHIPPED.getStatus()).setItemCount(1)
                .setPayableAmount(1250L).setReceiverName("Alice")
                .setReceiverDetailAddress("Snapshot address");
        CommerceOrderItemDO item = new CommerceOrderItemDO().setId(9101L).setOrderId(9001L)
                .setProductName("Snapshot tea").setQuantity(1).setTotalAmount(1250L);
        when(orderMapper.selectPageAdmin(request)).thenReturn(new PageResult<>(List.of(order), 1L));
        when(orderItemMapper.selectListByOrderId(9001L)).thenReturn(List.of(item));
        when(memberUserApi.getUser(101L)).thenReturn(new MemberUserRespDTO().setId(101L)
                .setNickname("Alice buyer").setMobile("13900000000"));

        PageResult<MerchantOrderRespVO> result = service.getAdminOrderPage(request);

        assertEquals(1L, result.getTotal());
        assertEquals("Alice buyer", result.getList().get(0).getBuyerNickname());
        assertEquals("Snapshot tea", result.getList().get(0).getItems().get(0).getProductName());
        verify(orderMapper).selectPageAdmin(request);
        verifyNoInteractions(merchantAccessService, memberAddressApi, productMapper, productSkuMapper);
    }

    @Test
    void shipOwnOrder_updatesOnlyPendingOrderForCurrentMerchantAndStore() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(accessContext(401L, 501L));
        CommerceOrderDO order = new CommerceOrderDO().setId(9001L).setMerchantId(401L).setStoreId(501L)
                .setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus());
        when(orderMapper.selectOwnedForUpdate(9001L, 401L, 501L)).thenReturn(order);
        when(orderMapper.markShipped(eq(9001L), eq(401L), eq(501L), eq("SF Express"), eq("SF123"), any()))
                .thenReturn(1);

        service.shipOwnOrder(new MerchantOrderShipReqVO().setId(9001L)
                .setLogisticsCompany("  SF Express ").setTrackingNo(" SF123 "));

        verify(orderMapper).selectOwnedForUpdate(9001L, 401L, 501L);
        verify(orderMapper).markShipped(eq(9001L), eq(401L), eq(501L), eq("SF Express"), eq("SF123"), any());
        verify(outboxEventAppender).append(eq(CommerceOutboxEventTypeEnum.ORDER_SHIPPED), eq(9001L), any());
    }

    @Test
    void shipOwnOrder_rejectsMissingForeignOrderAndDoesNotUpdate() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(accessContext(401L, 501L));
        when(orderMapper.selectOwnedForUpdate(9001L, 401L, 501L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.shipOwnOrder(new MerchantOrderShipReqVO().setId(9001L)
                        .setLogisticsCompany("SF Express").setTrackingNo("SF123")));

        assertEquals(ORDER_NOT_FOUND.getCode(), error.getCode());
        verify(orderMapper, never()).markShipped(anyLong(), anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void shipOwnOrder_rejectsAlreadyShippedOrder() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(accessContext(401L, 501L));
        when(orderMapper.selectOwnedForUpdate(9001L, 401L, 501L)).thenReturn(new CommerceOrderDO()
                .setId(9001L).setMerchantId(401L).setStoreId(501L)
                .setStatus(OrderStatusEnum.SHIPPED.getStatus()));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.shipOwnOrder(new MerchantOrderShipReqVO().setId(9001L)
                        .setLogisticsCompany("SF Express").setTrackingNo("SF123")));

        assertEquals(ORDER_SHIP_STATE_INVALID.getCode(), error.getCode());
        verify(orderMapper, never()).markShipped(anyLong(), anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void shipOwnOrder_rejectsActiveRefundBeforeShipping() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(accessContext(401L, 501L));
        for (Integer refundStatus : List.of(RefundStatusEnum.REQUESTED.getStatus(),
                RefundStatusEnum.APPROVED.getStatus(), RefundStatusEnum.SUCCESS.getStatus())) {
            reset(outboxEventAppender);
            when(orderMapper.selectOwnedForUpdate(9001L, 401L, 501L)).thenReturn(new CommerceOrderDO()
                    .setId(9001L).setMerchantId(401L).setStoreId(501L)
                    .setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus())
                    .setRefundStatus(refundStatus));

            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.shipOwnOrder(new MerchantOrderShipReqVO().setId(9001L)
                            .setLogisticsCompany("SF Express").setTrackingNo("SF123")));

            assertEquals(ORDER_SHIP_REFUND_CONFLICT.getCode(), error.getCode());
            verify(orderMapper, never()).markShipped(anyLong(), anyLong(), anyLong(), anyString(), anyString(), any());
            verify(outboxEventAppender, never()).append(any(), any(), any());
        }
    }

    @Test
    void getOwnPersonalPendingShipmentPage_scopesBySellerAndReturnsBuyerAndSnapshot() {
        CommerceOrderDO order = new CommerceOrderDO().setId(9002L).setOrderNo("C-personal")
                .setMemberUserId(202L).setSellerType(2).setSellerUserId(101L)
                .setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus()).setItemCount(1)
                .setTotalAmount(800L).setPayableAmount(800L).setReceiverName("Buyer")
                .setReceiverDetailAddress("Personal snapshot");
        when(orderMapper.selectPagePersonalPendingShipment(any(MerchantOrderPageReqVO.class), eq(101L)))
                .thenReturn(new PageResult<>(List.of(order), 1L));
        when(orderItemMapper.selectListByOrderId(9002L)).thenReturn(List.of());
        when(memberUserApi.getUser(202L)).thenReturn(new MemberUserRespDTO().setId(202L)
                .setNickname("Buyer").setMobile("13800000000"));

        PageResult<PersonalSellerOrderRespVO> result = service.getOwnPersonalPendingShipmentPage(101L,
                new MerchantOrderPageReqVO());

        assertEquals(1L, result.getTotal());
        assertEquals(202L, result.getList().get(0).getBuyerUserId());
        assertEquals("Personal snapshot", result.getList().get(0).getReceiverDetailAddress());
        assertEquals("Buyer", result.getList().get(0).getBuyerNickname());
        verify(orderMapper).selectPagePersonalPendingShipment(any(MerchantOrderPageReqVO.class), eq(101L));
    }

    @Test
    void shipPersonalOrder_scopesBySellerAndMovesOnlyPendingOrder() {
        CommerceOrderDO order = new CommerceOrderDO().setId(9002L).setSellerType(2).setSellerUserId(101L)
                .setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus());
        when(orderMapper.selectPersonalSellerForUpdate(9002L, 101L)).thenReturn(order);
        when(orderMapper.markPersonalShipped(eq(9002L), eq(101L), eq("SF Express"), eq("SF123"), any()))
                .thenReturn(1);

        service.shipPersonalOrder(101L, new MerchantOrderShipReqVO().setId(9002L)
                .setLogisticsCompany(" SF Express ").setTrackingNo(" SF123 "));

        verify(orderMapper).selectPersonalSellerForUpdate(9002L, 101L);
        verify(orderMapper).markPersonalShipped(eq(9002L), eq(101L), eq("SF Express"), eq("SF123"), any());
        verify(outboxEventAppender).append(eq(CommerceOutboxEventTypeEnum.ORDER_SHIPPED), eq(9002L), any());
    }

    @Test
    void shipPersonalOrder_rejectsForeignSellerOrder() {
        when(orderMapper.selectPersonalSellerForUpdate(9002L, 101L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.shipPersonalOrder(101L, new MerchantOrderShipReqVO().setId(9002L)
                        .setLogisticsCompany("SF Express").setTrackingNo("SF123")));

        assertEquals(ORDER_NOT_FOUND.getCode(), error.getCode());
        verify(orderMapper, never()).markPersonalShipped(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void confirmReceipt_rejectsActiveRefund() {
        for (Integer refundStatus : List.of(RefundStatusEnum.REQUESTED.getStatus(),
                RefundStatusEnum.APPROVED.getStatus(), RefundStatusEnum.SUCCESS.getStatus())) {
            reset(outboxEventAppender);
            when(orderMapper.selectOwnedForUpdate(101L, 9001L)).thenReturn(new CommerceOrderDO()
                    .setId(9001L).setMemberUserId(101L)
                    .setStatus(OrderStatusEnum.SHIPPED.getStatus()).setRefundStatus(refundStatus));

            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.confirmReceipt(101L, 9001L));

            assertEquals(ORDER_RECEIPT_REFUND_CONFLICT.getCode(), error.getCode());
            verify(orderMapper, never()).markCompleted(anyLong(), anyLong(), any());
            verify(outboxEventAppender, never()).append(any(), any(), any());
        }
    }

    private static MerchantAccessContext accessContext(Long merchantId, Long storeId) {
        return new MerchantAccessContext(merchant(merchantId), store(storeId, merchantId));
    }

    private void prepareSellable(Long userId, CartItemDO cart, ProductDO product, ProductSkuDO sku) {
        when(memberAddressApi.getOwnedAddressForUpdate(userId, 701L)).thenReturn(address(701L, userId));
        when(cartItemMapper.selectSelectedListByUserIdForUpdate(userId)).thenReturn(List.of(cart));
        when(productMapper.selectByIdForUpdate(product.getId())).thenReturn(product);
        when(productSkuMapper.selectByIdAndProductIdForUpdate(sku.getId(), product.getId())).thenReturn(sku);
        when(merchantMapper.selectById(product.getMerchantId())).thenReturn(merchant(product.getMerchantId()));
        when(storeMapper.selectById(product.getStoreId())).thenReturn(store(product.getStoreId(), product.getMerchantId()));
        when(productCategoryMapper.selectById(product.getCategoryId())).thenReturn(category(product.getCategoryId()));
    }

    private static MemberAddressRespDTO address(Long id, Long userId) {
        return new MemberAddressRespDTO().setId(id).setUserId(userId).setName("Alice")
                .setMobile("13800138000").setAreaId(310115).setAreaName("Shanghai Pudong")
                .setDetailAddress("Old address");
    }

    private static CartItemDO cartItem(Long id, Long userId, Long productId, Long skuId,
                                       int quantity, boolean selected) {
        return new CartItemDO().setId(id).setMemberUserId(userId).setProductId(productId)
                .setSkuId(skuId).setQuantity(quantity).setSelected(selected);
    }

    private static ProductDO product(Long id, Long merchantId, Long storeId, String name) {
        return new ProductDO().setId(id).setMerchantId(merchantId).setStoreId(storeId).setCategoryId(601L)
                .setName(name).setMainImageUrl("main.png").setAuditStatus(2).setSaleStatus(1);
    }

    private static ProductSkuDO sku(Long id, Long productId, Long merchantId, String code, long price, int stock) {
        return new ProductSkuDO().setId(id).setProductId(productId).setMerchantId(merchantId)
                .setCode(code).setPrice(price).setStock(stock).setStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    private static MerchantDO merchant(Long id) {
        return new MerchantDO().setId(id).setStatus(1);
    }

    private static StoreDO store(Long id, Long merchantId) {
        return new StoreDO().setId(id).setMerchantId(merchantId).setStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    private static ProductCategoryDO category(Long id) {
        return new ProductCategoryDO().setId(id).setStatus(CommonStatusEnum.ENABLE.getStatus());
    }
}
