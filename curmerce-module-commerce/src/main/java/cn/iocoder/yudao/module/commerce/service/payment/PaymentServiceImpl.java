package cn.iocoder.yudao.module.commerce.service.payment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCallbackRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCreateRespVO;
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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final String SIMULATED_METHOD = "SIMULATED";
    private static final Pattern PAYMENT_NO_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{7,63}");
    private static final Pattern CALLBACK_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}");
    private static final DateTimeFormatter PAYMENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private CommerceOrderMapper orderMapper;
    @Resource
    private CommercePaymentMapper paymentMapper;
    @Resource
    private CommerceOutboxEventAppender outboxEventAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateRespVO createPayment(Long userId, PaymentCreateReqVO reqVO) {
        memberUserApi.validateActiveUserForUpdate(userId);
        if (!SIMULATED_METHOD.equalsIgnoreCase(StrUtil.trim(reqVO.getPaymentMethod()))) {
            throw exception(PAYMENT_METHOD_INVALID);
        }

        CommerceOrderDO order = orderMapper.selectOwnedForUpdate(userId, reqVO.getOrderId());
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        if (!OrderStatusEnum.PENDING_PAYMENT.getStatus().equals(order.getStatus())) {
            throw exception(PAYMENT_ORDER_NOT_PAYABLE);
        }

        CommercePaymentDO existing = paymentMapper.selectByOrderIdForUpdate(order.getId());
        if (existing != null) {
            if (!order.getPayableAmount().equals(existing.getAmount())) {
                throw exception(PAYMENT_ALREADY_EXISTS);
            }
            return toCreateResponse(existing);
        }

        CommercePaymentDO payment = new CommercePaymentDO().setPaymentNo(generatePaymentNo())
                .setOrderId(order.getId()).setOrderNo(order.getOrderNo()).setMemberUserId(userId)
                .setAmount(order.getPayableAmount()).setStatus(PaymentStatusEnum.INITIATED.getStatus());
        paymentMapper.insert(payment);
        return toCreateResponse(payment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCallbackRespVO simulateCallback(PaymentSimulateCallbackReqVO reqVO) {
        String paymentNo = normalizePaymentNo(reqVO.getPaymentNo());
        String callbackId = normalizeCallbackId(reqVO.getCallbackId());
        Long paidAmount = reqVO.getPaidAmount();
        if (paidAmount == null || paidAmount < 0) {
            throw exception(PAYMENT_AMOUNT_INVALID);
        }

        // Read the immutable relationship first, then lock in order -> payment order.
        // Payment creation uses the same order -> payment lock order, avoiding a deadlock.
        CommercePaymentDO paymentRef = paymentMapper.selectByPaymentNo(paymentNo);
        if (paymentRef == null) {
            throw exception(PAYMENT_NOT_FOUND);
        }
        CommerceOrderDO order = orderMapper.selectByIdForUpdate(paymentRef.getOrderId());
        if (order == null) {
            throw exception(PAYMENT_ORDER_STATE_INVALID);
        }
        CommercePaymentDO payment = paymentMapper.selectByIdForUpdate(paymentRef.getId());
        if (payment == null || !paymentNo.equals(payment.getPaymentNo())) {
            throw exception(PAYMENT_NOT_FOUND);
        }

        if (PaymentStatusEnum.SUCCESS.getStatus().equals(payment.getStatus())) {
            if (!payment.getAmount().equals(paidAmount) || !callbackId.equals(payment.getCallbackId())) {
                throw exception(PAYMENT_CALLBACK_CONFLICT);
            }
            if (!OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus().equals(order.getStatus())) {
                throw exception(PAYMENT_ORDER_STATE_INVALID);
            }
            return toCallbackResponse(payment, order);
        }
        if (!PaymentStatusEnum.INITIATED.getStatus().equals(payment.getStatus())) {
            throw exception(PAYMENT_CALLBACK_CONFLICT);
        }
        if (!payment.getAmount().equals(paidAmount)) {
            throw exception(PAYMENT_AMOUNT_MISMATCH);
        }
        if (!OrderStatusEnum.PENDING_PAYMENT.getStatus().equals(order.getStatus())) {
            throw exception(PAYMENT_ORDER_STATE_INVALID);
        }

        LocalDateTime paidTime = LocalDateTime.now().withNano(0);
        if (paymentMapper.markSuccess(payment.getId(), callbackId, paidTime) != 1) {
            throw exception(PAYMENT_CALLBACK_CONFLICT);
        }
        if (orderMapper.markPaid(order.getId()) != 1) {
            throw exception(PAYMENT_ORDER_STATE_INVALID);
        }
        outboxEventAppender.append(CommerceOutboxEventTypeEnum.ORDER_PAID, order.getId(),
                orderPaidPayload(order, payment));
        payment.setStatus(PaymentStatusEnum.SUCCESS.getStatus()).setCallbackId(callbackId)
                .setPaidTime(paidTime);
        order.setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus());
        return toCallbackResponse(payment, order);
    }

    private Map<String, Object> orderPaidPayload(CommerceOrderDO order, CommercePaymentDO payment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("status", order.getStatus());
        payload.put("paymentId", payment.getId());
        payload.put("paymentNo", payment.getPaymentNo());
        payload.put("paidAmount", payment.getAmount());
        return payload;
    }

    private String normalizePaymentNo(String paymentNo) {
        String normalized = StrUtil.trim(paymentNo);
        if (normalized == null || !PAYMENT_NO_PATTERN.matcher(normalized).matches()) {
            throw exception(PAYMENT_NO_INVALID);
        }
        return normalized;
    }

    private String normalizeCallbackId(String callbackId) {
        String normalized = StrUtil.trim(callbackId);
        if (normalized == null || !CALLBACK_ID_PATTERN.matcher(normalized).matches()) {
            throw exception(PAYMENT_CALLBACK_ID_INVALID);
        }
        return normalized;
    }

    private String generatePaymentNo() {
        return "P" + LocalDateTime.now().format(PAYMENT_TIME_FORMAT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private PaymentCreateRespVO toCreateResponse(CommercePaymentDO payment) {
        return new PaymentCreateRespVO().setPaymentId(payment.getId()).setPaymentNo(payment.getPaymentNo())
                .setOrderId(payment.getOrderId()).setOrderNo(payment.getOrderNo()).setAmount(payment.getAmount())
                .setStatus(payment.getStatus());
    }

    private PaymentCallbackRespVO toCallbackResponse(CommercePaymentDO payment, CommerceOrderDO order) {
        return new PaymentCallbackRespVO().setPaymentId(payment.getId()).setPaymentNo(payment.getPaymentNo())
                .setOrderId(payment.getOrderId()).setPaymentStatus(payment.getStatus())
                .setOrderStatus(order.getStatus()).setPaidAmount(payment.getAmount())
                .setCallbackId(payment.getCallbackId());
    }
}
