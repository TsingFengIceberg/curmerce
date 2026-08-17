package cn.iocoder.yudao.module.commerce.service.payment;

import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCallbackRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCreateRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentSimulateCallbackReqVO;

public interface PaymentService {

    PaymentCreateRespVO createPayment(Long userId, PaymentCreateReqVO reqVO);

    PaymentCallbackRespVO simulateCallback(PaymentSimulateCallbackReqVO reqVO);
}
