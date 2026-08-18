package cn.iocoder.yudao.module.commerce.controller.app.refund;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundApplyReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundPageReqVO;
import cn.iocoder.yudao.module.commerce.service.refund.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 基础退款")
@RestController
@RequestMapping("/commerce/refund")
@Validated
public class RefundController {
    @Resource
    private RefundService refundService;

    @PostMapping("/apply")
    @Operation(summary = "申请基础退款", description = "当前为模拟退款，申请成功后同步返回退款成功记录；订单状态保持原交易状态")
    public CommonResult<RefundRespVO> apply(@Valid @RequestBody RefundApplyReqVO reqVO) {
        return success(refundService.applyRefund(getLoginUserId(), reqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "查询我的退款记录")
    public CommonResult<PageResult<RefundRespVO>> page(@Valid RefundPageReqVO reqVO) {
        return success(refundService.getRefundPage(getLoginUserId(), reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "查询我的退款详情")
    public CommonResult<RefundRespVO> get(@RequestParam Long id) {
        return success(refundService.getRefund(getLoginUserId(), id));
    }
}
