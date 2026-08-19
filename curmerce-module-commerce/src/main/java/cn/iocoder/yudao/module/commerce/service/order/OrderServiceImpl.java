package cn.iocoder.yudao.module.commerce.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderShipReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.*;
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
import cn.iocoder.yudao.module.commerce.dal.dataobject.payment.CommercePaymentDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.enums.payment.PaymentStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxEventTypeEnum;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxEventAppender;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class OrderServiceImpl implements OrderService {
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}");
    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long DEFAULT_PAYMENT_TIMEOUT_MINUTES = 30L;

    @Resource private MemberUserApi memberUserApi;
    @Resource private MemberAddressApi memberAddressApi;
    @Resource private CartItemMapper cartItemMapper;
    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper productSkuMapper;
    @Resource private ProductCategoryMapper productCategoryMapper;
    @Resource private MerchantMapper merchantMapper;
    @Resource private StoreMapper storeMapper;
    @Resource private CommerceOrderMapper orderMapper;
    @Resource private CommerceOrderItemMapper orderItemMapper;
    @Resource private MerchantAccessService merchantAccessService;
    @Resource private CommercePaymentMapper paymentMapper;
    @Resource private CommerceRefundMapper refundMapper;
    @Resource private CommerceOutboxEventAppender outboxEventAppender;
    @org.springframework.beans.factory.annotation.Value("${curmerce.order.payment-timeout-minutes:30}")
    private long paymentTimeoutMinutes = DEFAULT_PAYMENT_TIMEOUT_MINUTES;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateRespVO createOrder(Long userId, Long addressId, String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        memberUserApi.validateActiveUserForUpdate(userId);

        CommerceOrderDO existing = orderMapper.selectByUserAndIdempotencyKey(userId, key);
        if (existing != null) return toCreateResponse(existing);

        MemberAddressRespDTO address = memberAddressApi.getOwnedAddressForUpdate(userId, addressId);
        if (address == null) throw exception(ORDER_ADDRESS_NOT_AVAILABLE);

        // Serialize checkout attempts that are still racing to create the same
        // idempotency key. The second lookup is required because the first
        // transaction deletes its selected cart rows before it commits; once
        // the row lock is released, the retry must observe that committed order
        // rather than racing into a duplicate-key failure.
        List<CartItemDO> cartItems = cartItemMapper.selectSelectedListByUserIdForUpdate(userId);
        existing = orderMapper.selectByUserAndIdempotencyKey(userId, key);
        if (existing != null) return toCreateResponse(existing);

        if (cartItems.isEmpty()) throw exception(ORDER_CHECKOUT_EMPTY);

        List<CheckoutLine> lines = new ArrayList<>();
        Long merchantId = null;
        Long storeId = null;
        long totalAmount = 0L;
        int itemCount = 0;
        for (CartItemDO cartItem : cartItems) {
            if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1 || cartItem.getQuantity() > 99) {
                throw exception(ORDER_ITEM_NOT_AVAILABLE);
            }
            ProductDO product = productMapper.selectByIdForUpdate(cartItem.getProductId());
            ProductSkuDO sku = productSkuMapper.selectByIdAndProductIdForUpdate(cartItem.getSkuId(), cartItem.getProductId());
            if (!isSellable(product, sku)) throw exception(ORDER_ITEM_NOT_AVAILABLE);
            MerchantDO merchant = merchantMapper.selectById(product.getMerchantId());
            StoreDO store = storeMapper.selectById(product.getStoreId());
            ProductCategoryDO category = productCategoryMapper.selectById(product.getCategoryId());
            if (merchant == null || !Objects.equals(merchant.getStatus(), 1)
                    || store == null || !Objects.equals(store.getMerchantId(), product.getMerchantId())
                    || !CommonStatusEnum.isEnable(store.getStatus()) || !categoryTreeEnabled(category)
                    || !Objects.equals(sku.getMerchantId(), product.getMerchantId())) {
                throw exception(ORDER_ITEM_NOT_AVAILABLE);
            }
            if (merchantId == null) {
                merchantId = product.getMerchantId();
                storeId = product.getStoreId();
            } else if (!Objects.equals(merchantId, product.getMerchantId()) || !Objects.equals(storeId, product.getStoreId())) {
                throw exception(ORDER_CHECKOUT_MULTI_STORE);
            }
            long lineTotal = multiplyAmount(sku.getPrice(), cartItem.getQuantity());
            totalAmount = addAmount(totalAmount, lineTotal);
            itemCount = Math.addExact(itemCount, cartItem.getQuantity());
            lines.add(new CheckoutLine(cartItem, product, sku, lineTotal));
        }

        CommerceOrderDO order = new CommerceOrderDO().setOrderNo(generateOrderNo())
                .setMemberUserId(userId).setMerchantId(merchantId).setStoreId(storeId)
                .setIdempotencyKey(key).setStatus(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .setPaymentDeadline(LocalDateTime.now().plusMinutes(Math.max(1L, paymentTimeoutMinutes)))
                .setItemCount(itemCount).setTotalAmount(totalAmount).setPayableAmount(totalAmount)
                .setReceiverName(address.getName()).setReceiverMobile(address.getMobile())
                .setReceiverAreaId(address.getAreaId()).setReceiverAreaName(address.getAreaName())
                .setReceiverDetailAddress(address.getDetailAddress());
        orderMapper.insert(order);
        for (CheckoutLine line : lines) {
            if (productSkuMapper.deductStock(line.sku().getId(), line.cartItem().getQuantity()) != 1) {
                throw exception(ORDER_STOCK_INSUFFICIENT);
            }
            CommerceOrderItemDO item = new CommerceOrderItemDO().setOrderId(order.getId())
                    .setProductId(line.product().getId()).setSkuId(line.sku().getId())
                    .setProductName(line.product().getName()).setProductImageUrl(line.product().getMainImageUrl())
                    .setSkuCode(line.sku().getCode()).setSpecificationValues(line.sku().getSpecificationValues())
                    .setSkuImageUrl(line.sku().getImageUrl()).setPrice(line.sku().getPrice())
                    .setQuantity(line.cartItem().getQuantity()).setTotalAmount(line.lineTotal());
            orderItemMapper.insert(item);
        }
        cartItemMapper.deleteOwned(userId, lines.stream().map(line -> line.cartItem().getId()).toList());
        return toCreateResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderSummaryRespVO> getOrderPage(Long userId, OrderPageReqVO reqVO) {
        memberUserApi.validateActiveUser(userId);
        PageResult<CommerceOrderDO> page = orderMapper.selectPageOwned(userId, reqVO);
        return new PageResult<>(page.getList().stream().map(this::toSummary).toList(), page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailRespVO getOrder(Long userId, Long id) {
        memberUserApi.validateActiveUser(userId);
        CommerceOrderDO order = orderMapper.selectOwned(userId, id);
        if (order == null) throw exception(ORDER_NOT_FOUND);
        OrderDetailRespVO response = new OrderDetailRespVO();
        copySummary(order, response);
        response.setReceiverName(order.getReceiverName()).setReceiverMobile(order.getReceiverMobile())
                .setReceiverAreaId(order.getReceiverAreaId()).setReceiverAreaName(order.getReceiverAreaName())
                .setReceiverDetailAddress(order.getReceiverDetailAddress())
                .setShippingTime(order.getShippingTime()).setLogisticsCompany(order.getLogisticsCompany())
                .setTrackingNo(order.getTrackingNo()).setCompletionTime(order.getCompletionTime())
                .setItems(orderItemMapper.selectListByOrderId(order.getId()).stream().map(this::toItem).toList());
        response.setRefund(toRefundSummary(refundMapper.selectByOrderId(order.getId())));
        CommercePaymentDO payment = paymentMapper.selectByOrderId(order.getId());
        if (payment != null) {
            response.setPaymentNo(payment.getPaymentNo()).setPaymentStatus(payment.getStatus())
                    .setPaymentAmount(payment.getAmount()).setPaidTime(payment.getPaidTime());
        }
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceipt(Long userId, Long id) {
        memberUserApi.validateActiveUserForUpdate(userId);
        CommerceOrderDO order = orderMapper.selectOwnedForUpdate(userId, id);
        if (order == null) {
            // Keep foreign and missing orders indistinguishable to the caller.
            throw exception(ORDER_NOT_FOUND);
        }
        if (!OrderStatusEnum.SHIPPED.getStatus().equals(order.getStatus())) {
            throw exception(ORDER_RECEIPT_STATE_INVALID);
        }
        if (hasActiveRefund(order)) {
            throw exception(ORDER_RECEIPT_REFUND_CONFLICT);
        }
        LocalDateTime completionTime = nowPersisted();
        if (orderMapper.markCompleted(userId, id, completionTime) != 1) {
            throw exception(ORDER_RECEIPT_STATE_INVALID);
        }
        outboxEventAppender.append(CommerceOutboxEventTypeEnum.ORDER_COMPLETED, order.getId(),
                orderPayload(order));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long id) {
        memberUserApi.validateActiveUserForUpdate(userId);
        CommerceOrderDO order = orderMapper.selectOwnedForUpdate(userId, id);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        if (!OrderStatusEnum.PENDING_PAYMENT.getStatus().equals(order.getStatus())) {
            throw exception(ORDER_CANCEL_STATE_INVALID);
        }
        restoreOrderStock(order.getId());
        CommercePaymentDO payment = paymentMapper.selectByOrderIdForUpdate(order.getId());
        if (payment != null && PaymentStatusEnum.SUCCESS.getStatus().equals(payment.getStatus())) {
            throw exception(ORDER_CANCEL_STATE_INVALID);
        }
        if (payment != null && PaymentStatusEnum.INITIATED.getStatus().equals(payment.getStatus())
                && paymentMapper.markCanceled(payment.getId()) != 1) {
            throw exception(ORDER_CANCEL_STATE_INVALID);
        }
        if (orderMapper.markCanceled(userId, id) != 1) {
            throw exception(ORDER_CANCEL_STATE_INVALID);
        }
        outboxEventAppender.append(CommerceOutboxEventTypeEnum.ORDER_CANCELED, order.getId(),
                orderPayload(order));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int closeExpiredPendingPaymentOrders(LocalDateTime cutoffTime, int batchSize) {
        List<CommerceOrderDO> orders = orderMapper.selectExpiredPendingPaymentForUpdate(cutoffTime, batchSize);
        int closed = 0;
        for (CommerceOrderDO order : orders) {
            restoreOrderStock(order.getId());
            CommercePaymentDO payment = paymentMapper.selectByOrderIdForUpdate(order.getId());
            if (payment != null && PaymentStatusEnum.SUCCESS.getStatus().equals(payment.getStatus())) {
                throw exception(ORDER_CANCEL_STATE_INVALID);
            }
            if (payment != null && PaymentStatusEnum.INITIATED.getStatus().equals(payment.getStatus())
                    && paymentMapper.markCanceled(payment.getId()) != 1) {
                throw exception(ORDER_CANCEL_STATE_INVALID);
            }
            if (orderMapper.markCanceled(order.getId()) != 1) {
                throw exception(ORDER_CANCEL_STATE_INVALID);
            }
            outboxEventAppender.append(CommerceOutboxEventTypeEnum.ORDER_CANCELED, order.getId(),
                    orderPayload(order));
            closed++;
        }
        return closed;
    }

    private void restoreOrderStock(Long orderId) {
        for (CommerceOrderItemDO item : orderItemMapper.selectListByOrderId(orderId)) {
            if (productSkuMapper.restoreStock(item.getSkuId(), item.getQuantity()) != 1) {
                throw exception(ORDER_STOCK_RESTORE_FAILED);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<MerchantOrderRespVO> getOwnPendingShipmentPage(MerchantOrderPageReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        PageResult<CommerceOrderDO> page = orderMapper.selectPagePendingShipment(reqVO,
                context.merchant().getId(), context.store().getId());
        return new PageResult<>(page.getList().stream().map(this::toMerchantResponse).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipOwnOrder(MerchantOrderShipReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        CommerceOrderDO order = orderMapper.selectOwnedForUpdate(reqVO.getId(), context.merchant().getId(),
                context.store().getId());
        if (order == null) {
            // Keep foreign and missing orders indistinguishable to the caller.
            throw exception(ORDER_NOT_FOUND);
        }
        if (!OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus().equals(order.getStatus())) {
            throw exception(ORDER_SHIP_STATE_INVALID);
        }
        if (hasActiveRefund(order)) {
            throw exception(ORDER_SHIP_REFUND_CONFLICT);
        }
        String logisticsCompany = StrUtil.trim(reqVO.getLogisticsCompany());
        String trackingNo = StrUtil.trim(reqVO.getTrackingNo());
        if (StrUtil.isBlank(logisticsCompany) || StrUtil.isBlank(trackingNo)) {
            throw exception(ORDER_SHIPPING_INFO_INVALID);
        }
        LocalDateTime shippingTime = nowPersisted();
        if (orderMapper.markShipped(order.getId(), context.merchant().getId(), context.store().getId(),
                logisticsCompany, trackingNo, shippingTime) != 1) {
            throw exception(ORDER_SHIP_STATE_INVALID);
        }
        outboxEventAppender.append(CommerceOutboxEventTypeEnum.ORDER_SHIPPED, order.getId(),
                orderPayload(order));
    }

    private boolean hasActiveRefund(CommerceOrderDO order) {
        Integer refundStatus = order.getRefundStatus();
        return refundStatus != null
                && (RefundStatusEnum.REQUESTED.getStatus().equals(refundStatus)
                || RefundStatusEnum.APPROVED.getStatus().equals(refundStatus)
                || RefundStatusEnum.SUCCESS.getStatus().equals(refundStatus));
    }

    private LocalDateTime nowPersisted() {
        return LocalDateTime.now().withNano(0);
    }

    private Map<String, Object> orderPayload(CommerceOrderDO order) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("status", order.getStatus());
        payload.put("refundStatus", order.getRefundStatus());
        return payload;
    }

    private boolean isSellable(ProductDO product, ProductSkuDO sku) {
        return product != null && Objects.equals(product.getAuditStatus(), 2)
                && Objects.equals(product.getSaleStatus(), 1)
                && sku != null && CommonStatusEnum.isEnable(sku.getStatus())
                && sku.getPrice() != null && sku.getPrice() >= 0
                && sku.getStock() != null && sku.getStock() >= 0;
    }

    private boolean categoryTreeEnabled(ProductCategoryDO category) {
        if (category == null || !CommonStatusEnum.isEnable(category.getStatus())) return false;
        Set<Long> seen = new HashSet<>();
        Long current = category.getId();
        while (current != null) {
            if (!seen.add(current)) return false;
            ProductCategoryDO node = productCategoryMapper.selectById(current);
            if (node == null || !CommonStatusEnum.isEnable(node.getStatus())) return false;
            current = node.getParentId();
        }
        return true;
    }

    private String normalizeIdempotencyKey(String value) {
        String key = StrUtil.trim(value);
        if (key == null || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw exception(ORDER_IDEMPOTENCY_KEY_INVALID);
        }
        return key;
    }

    private long multiplyAmount(Long price, int quantity) {
        if (price == null || price < 0) throw exception(ORDER_ITEM_NOT_AVAILABLE);
        try {
            return Math.multiplyExact(price, (long) quantity);
        } catch (ArithmeticException ex) {
            throw exception(ORDER_AMOUNT_OVERFLOW);
        }
    }

    private long addAmount(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw exception(ORDER_AMOUNT_OVERFLOW);
        }
    }

    private String generateOrderNo() {
        return "C" + LocalDateTime.now().format(ORDER_TIME_FORMAT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private OrderCreateRespVO toCreateResponse(CommerceOrderDO order) {
        return new OrderCreateRespVO().setOrderId(order.getId()).setOrderNo(order.getOrderNo())
                .setStatus(order.getStatus()).setPayableAmount(order.getPayableAmount());
    }

    private OrderSummaryRespVO toSummary(CommerceOrderDO order) {
        OrderSummaryRespVO response = new OrderSummaryRespVO();
        copySummary(order, response);
        return response;
    }

    private MerchantOrderRespVO toMerchantResponse(CommerceOrderDO order) {
        MerchantOrderRespVO response = new MerchantOrderRespVO().setId(order.getId())
                .setOrderNo(order.getOrderNo()).setMemberUserId(order.getMemberUserId())
                .setMerchantId(order.getMerchantId()).setStoreId(order.getStoreId()).setStatus(order.getStatus())
                .setItemCount(order.getItemCount()).setTotalAmount(order.getTotalAmount())
                .setPayableAmount(order.getPayableAmount()).setReceiverName(order.getReceiverName())
                .setReceiverMobile(order.getReceiverMobile()).setReceiverAreaId(order.getReceiverAreaId())
                .setReceiverAreaName(order.getReceiverAreaName())
                .setReceiverDetailAddress(order.getReceiverDetailAddress()).setShippingTime(order.getShippingTime())
                .setLogisticsCompany(order.getLogisticsCompany()).setTrackingNo(order.getTrackingNo())
                .setCompletionTime(order.getCompletionTime())
                .setCreateTime(order.getCreateTime())
                .setItems(orderItemMapper.selectListByOrderId(order.getId()).stream().map(this::toItem).toList());
        MemberUserRespDTO buyer = memberUserApi.getUser(order.getMemberUserId());
        if (buyer != null) {
            response.setBuyerMobile(buyer.getMobile()).setBuyerNickname(buyer.getNickname())
                    .setBuyerEmail(buyer.getEmail());
        }
        return response;
    }

    private void copySummary(CommerceOrderDO order, OrderSummaryRespVO response) {
        response.setId(order.getId()).setOrderNo(order.getOrderNo()).setMerchantId(order.getMerchantId())
                .setStoreId(order.getStoreId()).setStatus(order.getStatus()).setItemCount(order.getItemCount())
                .setTotalAmount(order.getTotalAmount()).setPayableAmount(order.getPayableAmount())
                .setRefundStatus(order.getRefundStatus() == null ? 0 : order.getRefundStatus())
                .setCreateTime(order.getCreateTime()).setCompletionTime(order.getCompletionTime());
    }

    private OrderItemRespVO toItem(CommerceOrderItemDO item) {
        return new OrderItemRespVO().setId(item.getId()).setProductId(item.getProductId()).setSkuId(item.getSkuId())
                .setProductName(item.getProductName()).setProductImageUrl(item.getProductImageUrl())
                .setSkuCode(item.getSkuCode()).setSpecificationValues(item.getSpecificationValues())
                .setSkuImageUrl(item.getSkuImageUrl()).setPrice(item.getPrice()).setQuantity(item.getQuantity())
                .setTotalAmount(item.getTotalAmount());
    }

    private RefundSummaryRespVO toRefundSummary(CommerceRefundDO refund) {
        if (refund == null) return null;
        return new RefundSummaryRespVO().setId(refund.getId()).setRefundNo(refund.getRefundNo())
                .setAmount(refund.getAmount()).setStatus(refund.getStatus()).setReason(refund.getReason())
                .setRequestedTime(refund.getRequestedTime()).setProcessedTime(refund.getProcessedTime());
    }

    private record CheckoutLine(CartItemDO cartItem, ProductDO product, ProductSkuDO sku, long lineTotal) { }
}
