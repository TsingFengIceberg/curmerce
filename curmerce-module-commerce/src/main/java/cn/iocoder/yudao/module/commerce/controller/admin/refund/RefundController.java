package cn.iocoder.yudao.module.commerce.controller.admin.refund;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundAuditReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundCallbackReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.refund.vo.RefundRespVO;
import cn.iocoder.yudao.module.commerce.service.refund.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - Curmerce 退款售后")
@RestController("commerceAdminRefundController")
@RequestMapping("/commerce/refund")
@Validated
public class RefundController {

    @Resource
    private RefundService refundService;

    @GetMapping("/page")
    @Operation(summary = "查询退款分页")
    @PreAuthorize("@ss.hasPermission('commerce:refund:query')")
    public CommonResult<PageResult<RefundRespVO>> page(@Valid RefundPageReqVO reqVO) {
        return success(refundService.getAdminRefundPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "查询退款详情")
    @PreAuthorize("@ss.hasPermission('commerce:refund:query')")
    public CommonResult<RefundRespVO> get(@RequestParam Long id) {
        return success(refundService.getAdminRefund(id));
    }

    @PutMapping("/approve")
    @Operation(summary = "审核通过退款")
    @PreAuthorize("@ss.hasPermission('commerce:refund:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody RefundAuditReqVO reqVO) {
        refundService.approveRefund(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回退款")
    @PreAuthorize("@ss.hasPermission('commerce:refund:audit')")
    public CommonResult<Boolean> reject(@Valid @RequestBody RefundAuditReqVO reqVO) {
        refundService.rejectRefund(getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/simulate-callback")
    @Operation(summary = "模拟退款渠道回调")
    @PreAuthorize("@ss.hasPermission('commerce:refund:callback')")
    public CommonResult<RefundRespVO> simulateCallback(@Valid @RequestBody RefundCallbackReqVO reqVO) {
        return success(refundService.simulateCallback(reqVO));
    }

    @GetMapping("/page-own")
    @Operation(summary = "查询自己商家的退款")
    @PreAuthorize("@ss.hasPermission('commerce:refund:self-query')")
    public CommonResult<PageResult<RefundRespVO>> pageOwn(@Valid RefundPageReqVO reqVO) {
        return success(refundService.getMerchantRefundPage(reqVO));
    }

    @GetMapping("/get-own")
    @Operation(summary = "查询自己商家的退款详情")
    @PreAuthorize("@ss.hasPermission('commerce:refund:self-query')")
    public CommonResult<RefundRespVO> getOwn(@RequestParam Long id) {
        return success(refundService.getMerchantRefund(id));
    }

    @PutMapping("/approve-own")
    @Operation(summary = "审核通过自己商家的退款")
    @PreAuthorize("@ss.hasPermission('commerce:refund:self-audit')")
    public CommonResult<Boolean> approveOwn(@Valid @RequestBody RefundAuditReqVO reqVO) {
        refundService.approveMerchantRefund(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/reject-own")
    @Operation(summary = "驳回自己商家的退款")
    @PreAuthorize("@ss.hasPermission('commerce:refund:self-audit')")
    public CommonResult<Boolean> rejectOwn(@Valid @RequestBody RefundAuditReqVO reqVO) {
        refundService.rejectMerchantRefund(getLoginUserId(), reqVO);
        return success(true);
    }
}
