package cn.iocoder.yudao.module.commerce.controller.admin.reliability;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxPublisherService;
import cn.iocoder.yudao.module.commerce.service.reconciliation.CommerceReconciliationService;
import cn.iocoder.yudao.module.commerce.service.outbox.kafka.CommerceKafkaReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Resource
    private cn.iocoder.yudao.module.commerce.service.release.ReleaseDistributedPurchaseQueue releaseQueue;
    @Resource
    private cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseReservationMapper releaseReservationMapper;
    @Autowired(required = false)
    private CommerceKafkaReceiptService kafkaReceiptService;

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

    @GetMapping("/kafka/status")
    @Operation(summary = "查询 Kafka 消费处理状态")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:query')")
    public CommonResult<Map<Integer, Long>> kafkaStatus() {
        return success(kafkaReceiptService == null ? Map.of() : kafkaReceiptService.statusCounts());
    }

    @PostMapping("/kafka/replay-failed")
    @Operation(summary = "重放失败的 Kafka 消费事件")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:operate')")
    public CommonResult<Integer> replayKafka(@RequestParam(defaultValue = "100") int limit) {
        return success(kafkaReceiptService == null ? 0 : kafkaReceiptService.replayFailed(limit));
    }

    @GetMapping("/release/queue-status")
    @Operation(summary = "查询限时发售分布式队列状态")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:query')")
    public CommonResult<cn.iocoder.yudao.module.commerce.service.release.ReleaseDistributedPurchaseQueue.QueueSnapshot> releaseQueueStatus() {
        return success(releaseQueue.snapshot());
    }

    @GetMapping("/release/dead-letters")
    @Operation(summary = "查询限时发售死信")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:query')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.commerce.service.release.ReleaseDistributedPurchaseQueue.DeadLetter>> releaseDeadLetters(
            @RequestParam(defaultValue = "50") int limit) {
        return success(releaseQueue.deadLetters(limit));
    }

    @PostMapping("/release/dead-letters/replay")
    @Operation(summary = "重放限时发售死信")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:operate')")
    public CommonResult<Boolean> replayReleaseDeadLetter(@RequestParam String entryId) {
        return success(releaseQueue.replayDeadLetter(entryId));
    }

    @GetMapping("/release/reservations/status")
    @Operation(summary = "查询限时发售 Redis 预占恢复状态")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:query')")
    public CommonResult<Map<String, Long>> releaseReservationStatus() {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        result.put("committed", releaseReservationMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO>()
                .eq(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO::getTenantId, releaseReservationMapper.tenantId())
                .eq(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO::getStatus, cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO.COMMITTED)));
        result.put("finalized", releaseReservationMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO>()
                .eq(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO::getTenantId, releaseReservationMapper.tenantId())
                .eq(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO::getStatus, cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO.FINALIZED)));
        result.put("dead", releaseReservationMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO>()
                .eq(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO::getTenantId, releaseReservationMapper.tenantId())
                .eq(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO::getStatus, cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO.DEAD)));
        return success(Map.copyOf(result));
    }

    @PostMapping("/release/reservations/replay")
    @Operation(summary = "重放限时发售 Redis 预占死信")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:operate')")
    public CommonResult<Boolean> replayReleaseReservation(@RequestParam Long id) {
        return success(releaseReservationMapper.replayDeadFinalization(id) == 1);
    }
}
