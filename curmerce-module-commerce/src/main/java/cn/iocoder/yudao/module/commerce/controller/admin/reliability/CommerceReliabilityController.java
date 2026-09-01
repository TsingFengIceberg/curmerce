package cn.iocoder.yudao.module.commerce.controller.admin.reliability;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxPublisherService;
import cn.iocoder.yudao.module.commerce.service.reconciliation.CommerceReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Operational endpoints for inspecting and repairing local commerce reliability state. */
@Tag(name = "管理后台 - Curmerce 可靠性运维")
@RestController("commerceReliabilityController")
@RequestMapping("/commerce/reliability")
@Validated
public class CommerceReliabilityController {

    @Resource
    private CommerceOutboxPublisherService outboxPublisherService;
    @Resource
    private CommerceReconciliationService reconciliationService;

    @GetMapping("/outbox/status")
    @Operation(summary = "查询 Outbox 状态统计")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:query')")
    public CommonResult<Map<Integer, Long>> outboxStatus() {
        return success(outboxPublisherService.statusCounts());
    }

    @PostMapping("/outbox/publish")
    @Operation(summary = "立即发布待处理 Outbox")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:operate')")
    public CommonResult<Integer> publish(@RequestParam(defaultValue = "100") int batchSize) {
        return success(outboxPublisherService.publishPending(batchSize));
    }

    @PostMapping("/outbox/retry-dead")
    @Operation(summary = "重新入队死信 Outbox")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:operate')")
    public CommonResult<Integer> retryDead(@RequestParam(defaultValue = "100") int limit) {
        return success(outboxPublisherService.retryDead(limit));
    }

    @PostMapping("/reconciliation/scan")
    @Operation(summary = "扫描订单支付退款一致性")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:operate')")
    public CommonResult<Integer> scan(@RequestParam(defaultValue = "100") int batchSize) {
        return success(reconciliationService.scanAndOpenIssues(batchSize));
    }

    @PutMapping("/reconciliation/repair")
    @Operation(summary = "执行安全对账修复")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:operate')")
    public CommonResult<Boolean> repair(@RequestParam Long id) {
        return success(reconciliationService.repairIssue(id));
    }

    @PutMapping("/reconciliation/resolve")
    @Operation(summary = "手动关闭对账问题")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:operate')")
    public CommonResult<Boolean> resolve(@RequestParam Long id) {
        return success(reconciliationService.resolveIssue(id));
    }
}
