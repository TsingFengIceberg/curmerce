package cn.iocoder.yudao.module.commerce.service.refund;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundApplyReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundRespVO;

public interface RefundService {

    RefundRespVO applyRefund(Long userId, RefundApplyReqVO reqVO);

    PageResult<RefundRespVO> getRefundPage(Long userId, RefundPageReqVO reqVO);

    RefundRespVO getRefund(Long userId, Long id);
}
