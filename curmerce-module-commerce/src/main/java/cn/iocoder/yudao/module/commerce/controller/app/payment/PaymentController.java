package cn.iocoder.yudao.module.commerce.controller.app.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCallbackRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentCreateRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.payment.vo.PaymentSimulateCallbackReqVO;
import cn.iocoder.yudao.module.commerce.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 模拟支付")
@RestController
@RequestMapping("/commerce/payment")
@Validated
public class PaymentController {
    @Resource
    private PaymentService paymentService;

    @PostMapping("/create")
    @Operation(summary = "创建模拟支付单")
    public CommonResult<PaymentCreateRespVO> create(@Valid @RequestBody PaymentCreateReqVO reqVO) {
        return success(paymentService.createPayment(getLoginUserId(), reqVO));
    }

    @PostMapping("/simulate-callback")
    @PermitAll
    @Operation(summary = "接收模拟支付成功回调")
    public CommonResult<PaymentCallbackRespVO> simulateCallback(
            @Valid @RequestBody PaymentSimulateCallbackReqVO reqVO) {
        return success(paymentService.simulateCallback(reqVO));
    }
}
