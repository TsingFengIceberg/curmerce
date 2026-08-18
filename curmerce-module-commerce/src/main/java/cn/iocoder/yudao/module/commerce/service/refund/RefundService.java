package cn.iocoder.yudao.module.commerce.service.refund;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundApplyReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundAuditReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundCallbackReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundPageReqVO;

public interface RefundService {

    RefundRespVO applyRefund(Long userId, RefundApplyReqVO reqVO);

    PageResult<RefundRespVO> getRefundPage(Long userId,
                                           cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundPageReqVO reqVO);

    RefundRespVO getRefund(Long userId, Long id);

    PageResult<RefundRespVO> getAdminRefundPage(RefundPageReqVO reqVO);

    RefundRespVO getAdminRefund(Long id);

    void approveRefund(Long reviewerUserId, RefundAuditReqVO reqVO);

    void rejectRefund(Long reviewerUserId, RefundAuditReqVO reqVO);

    RefundRespVO simulateCallback(RefundCallbackReqVO reqVO);

    PageResult<RefundRespVO> getMerchantRefundPage(RefundPageReqVO reqVO);

    RefundRespVO getMerchantRefund(Long id);

    void approveMerchantRefund(Long reviewerUserId, RefundAuditReqVO reqVO);

    void rejectMerchantRefund(Long reviewerUserId, RefundAuditReqVO reqVO);
}
