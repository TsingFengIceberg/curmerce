package cn.iocoder.yudao.module.commerce.service.refund;

import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundApplyReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundRespVO;

public interface RefundService {

    RefundRespVO applyRefund(Long userId, RefundApplyReqVO reqVO);
}
