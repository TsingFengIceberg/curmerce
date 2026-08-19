package cn.iocoder.yudao.module.commerce.service.refund;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundAuditReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundCallbackReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundApplyReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.refund.CommerceRefundDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.refund.CommerceRefundMapper;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class RefundServiceImpl implements RefundService {
    private static final DateTimeFormatter REFUND_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern CALLBACK_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private CommerceOrderMapper orderMapper;
    @Resource
    private CommerceRefundMapper refundMapper;
    @Resource
    private MerchantAccessService merchantAccessService;

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

        CommerceRefundDO refund = new CommerceRefundDO().setRefundNo(generateRefundNo())
                .setOrderId(order.getId()).setOrderNo(order.getOrderNo()).setMemberUserId(userId)
                .setAmount(order.getPayableAmount()).setStatus(RefundStatusEnum.REQUESTED.getStatus())
                .setReason(StrUtil.trim(reqVO.getReason())).setRequestedTime(nowPersisted());
        refundMapper.insert(refund);
        if (orderMapper.markRefundStatus(order.getId(), RefundStatusEnum.REQUESTED.getStatus()) != 1) {
            throw exception(REFUND_STATE_INVALID);
        }
        return toResponse(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RefundRespVO> getRefundPage(Long userId,
                                                  cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundPageReqVO reqVO) {
        memberUserApi.validateActiveUser(userId);
        PageResult<CommerceRefundDO> page = refundMapper.selectPageOwned(userId, reqVO);
        return toPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundRespVO getRefund(Long userId, Long id) {
        memberUserApi.validateActiveUser(userId);
        CommerceRefundDO refund = refundMapper.selectOwned(userId, id);
        if (refund == null) {
            throw exception(REFUND_NOT_FOUND);
        }
        return toResponse(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RefundRespVO> getAdminRefundPage(RefundPageReqVO reqVO) {
        return toPage(refundMapper.selectPageAdmin(reqVO));
    }

    @Override
    @Transactional(readOnly = true)
    public RefundRespVO getAdminRefund(Long id) {
        CommerceRefundDO refund = refundMapper.selectById(id);
        if (refund == null) {
            throw exception(REFUND_NOT_FOUND);
        }
        return toResponse(refund);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRefund(Long reviewerUserId, RefundAuditReqVO reqVO) {
        review(reqVO.getId(), reviewerUserId, reqVO.getRemark(), true, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRefund(Long reviewerUserId, RefundAuditReqVO reqVO) {
        review(reqVO.getId(), reviewerUserId, reqVO.getRemark(), false, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundRespVO simulateCallback(RefundCallbackReqVO reqVO) {
        String refundNo = StrUtil.trim(reqVO.getRefundNo());
        String callbackId = normalizeCallbackId(reqVO.getCallbackId());
        CommerceRefundDO refund = refundMapper.selectByRefundNoForUpdate(refundNo);
        if (refund == null) {
            throw exception(REFUND_NOT_FOUND);
        }
        boolean success = Boolean.TRUE.equals(reqVO.getSuccess());
        if (RefundStatusEnum.SUCCESS.getStatus().equals(refund.getStatus())
                || RefundStatusEnum.FAILED.getStatus().equals(refund.getStatus())) {
            if (callbackId.equals(refund.getCallbackId()) && Boolean.valueOf(success).equals(refund.getCallbackSuccess())) {
                return toResponse(refund);
            }
            throw exception(REFUND_CALLBACK_CONFLICT);
        }
        if (!RefundStatusEnum.APPROVED.getStatus().equals(refund.getStatus())) {
            throw exception(REFUND_STATE_INVALID);
        }
        // MySQL DATETIME has second precision in the current schema. Truncate
        // before writing so the first callback response and an idempotent replay
        // expose the same durable timestamp.
        LocalDateTime processedTime = nowPersisted();
        if (refundMapper.markCallback(refund.getId(), callbackId, success, processedTime) != 1) {
            throw exception(REFUND_CALLBACK_CONFLICT);
        }
        Integer finalStatus = success ? RefundStatusEnum.SUCCESS.getStatus() : RefundStatusEnum.FAILED.getStatus();
        if (orderMapper.markRefundStatus(refund.getOrderId(), finalStatus) != 1) {
            throw exception(REFUND_STATE_INVALID);
        }
        refund.setStatus(finalStatus).setCallbackId(callbackId).setCallbackSuccess(success)
                .setProcessedTime(processedTime);
        return toResponse(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RefundRespVO> getMerchantRefundPage(RefundPageReqVO reqVO) {
        MerchantAccessContext access = merchantAccessService.requireApprovedOwner();
        return toPage(refundMapper.selectPageMerchant(reqVO, access.merchant().getId(), access.store().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public RefundRespVO getMerchantRefund(Long id) {
        return toResponse(requireMerchantRefund(id, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveMerchantRefund(Long reviewerUserId, RefundAuditReqVO reqVO) {
        MerchantAccessContext access = merchantAccessService.requireApprovedOwner();
        review(reqVO.getId(), reviewerUserId, reqVO.getRemark(), true, access);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectMerchantRefund(Long reviewerUserId, RefundAuditReqVO reqVO) {
        MerchantAccessContext access = merchantAccessService.requireApprovedOwner();
        review(reqVO.getId(), reviewerUserId, reqVO.getRemark(), false, access);
    }

    private void review(Long id, Long reviewerUserId, String remark, boolean approve,
                        MerchantAccessContext merchantAccess) {
        String normalizedRemark = StrUtil.trim(remark);
        if (!approve && StrUtil.isBlank(normalizedRemark)) {
            throw exception(REFUND_REVIEW_REMARK_INVALID);
        }
        CommerceRefundDO refund = merchantAccess == null
                ? refundMapper.selectByIdForUpdate(id)
                : requireMerchantRefund(id, true, merchantAccess);
        if (refund == null) {
            throw exception(REFUND_NOT_FOUND);
        }
        if (!RefundStatusEnum.REQUESTED.getStatus().equals(refund.getStatus())) {
            throw exception(REFUND_STATE_INVALID);
        }
        LocalDateTime reviewedTime = LocalDateTime.now();
        int updated = approve
                ? refundMapper.markApproved(id, reviewerUserId, reviewedTime, normalizedRemark)
                : refundMapper.markRejected(id, reviewerUserId, reviewedTime, normalizedRemark);
        if (updated != 1) {
            throw exception(REFUND_STATE_INVALID);
        }
        int orderStatus = approve ? RefundStatusEnum.APPROVED.getStatus() : RefundStatusEnum.REJECTED.getStatus();
        if (orderMapper.markRefundStatus(refund.getOrderId(), orderStatus) != 1) {
            throw exception(REFUND_STATE_INVALID);
        }
    }

    private CommerceRefundDO requireMerchantRefund(Long id, boolean forUpdate) {
        return requireMerchantRefund(id, forUpdate, merchantAccessService.requireApprovedOwner());
    }

    private CommerceRefundDO requireMerchantRefund(Long id, boolean forUpdate, MerchantAccessContext access) {
        CommerceRefundDO refund = forUpdate ? refundMapper.selectByIdForUpdate(id) : refundMapper.selectById(id);
        if (refund == null) {
            throw exception(REFUND_NOT_FOUND);
        }
        CommerceOrderDO order = orderMapper.selectById(refund.getOrderId());
        if (order == null || !access.merchant().getId().equals(order.getMerchantId())
                || !access.store().getId().equals(order.getStoreId())) {
            throw exception(REFUND_NOT_FOUND);
        }
        return refund;
    }

    private PageResult<RefundRespVO> toPage(PageResult<CommerceRefundDO> page) {
        return new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal());
    }

    private boolean isRefundable(Integer status) {
        return OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus().equals(status)
                || OrderStatusEnum.SHIPPED.getStatus().equals(status)
                || OrderStatusEnum.COMPLETED.getStatus().equals(status);
    }

    private String normalizeCallbackId(String callbackId) {
        String normalized = StrUtil.trim(callbackId);
        if (normalized == null || !CALLBACK_ID_PATTERN.matcher(normalized).matches()) {
            throw exception(REFUND_CALLBACK_ID_INVALID);
        }
        return normalized;
    }

    private String generateRefundNo() {
        return "R" + LocalDateTime.now().format(REFUND_TIME_FORMAT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private LocalDateTime nowPersisted() {
        return LocalDateTime.now().withNano(0);
    }

    private RefundRespVO toResponse(CommerceRefundDO refund) {
        return new RefundRespVO().setId(refund.getId()).setRefundNo(refund.getRefundNo())
                .setOrderId(refund.getOrderId()).setOrderNo(refund.getOrderNo()).setAmount(refund.getAmount())
                .setStatus(refund.getStatus()).setReason(refund.getReason())
                .setRequestedTime(refund.getRequestedTime()).setReviewerUserId(refund.getReviewerUserId())
                .setReviewedTime(refund.getReviewedTime()).setReviewRemark(refund.getReviewRemark())
                .setCallbackId(refund.getCallbackId()).setCallbackSuccess(refund.getCallbackSuccess())
                .setProcessedTime(refund.getProcessedTime());
    }
}
