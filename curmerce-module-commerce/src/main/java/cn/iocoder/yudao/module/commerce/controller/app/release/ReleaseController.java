package cn.iocoder.yudao.module.commerce.controller.app.release;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.*;
import cn.iocoder.yudao.module.commerce.service.release.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 限时发售")
@RestController("commerceAppReleaseController")
@RequestMapping("/commerce/release")
@Validated
public class ReleaseController {
    @Resource private ReleaseService releaseService;
    @Resource private cn.iocoder.yudao.module.commerce.service.release.ReleasePurchaseQueue purchaseQueue;
    @Resource private cn.iocoder.yudao.module.commerce.service.release.ReleaseDistributedPurchaseQueue distributedPurchaseQueue;
    @Resource private cn.iocoder.yudao.module.commerce.service.release.ReleaseKafkaPurchaseQueue kafkaPurchaseQueue;
    @GetMapping("/page") @PermitAll @Operation(summary = "查询公开限时发售活动")
    public CommonResult<PageResult<ReleaseRespVO>> page(@Valid cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePageReqVO reqVO) {
        return success(releaseService.getPublicPage(reqVO));
    }
    @GetMapping("/get") @PermitAll @Operation(summary = "查询限时发售详情")
    public CommonResult<ReleaseRespVO> get(@RequestParam Long id) { return success(releaseService.get(id, true)); }
    @PostMapping("/purchase") @Operation(summary = "购买限时发售商品")
    public CommonResult<ReleasePurchaseRespVO> purchase(@Valid @RequestBody ReleasePurchaseReqVO reqVO) {
        return success(releaseService.purchase(getLoginUserId(), reqVO));
    }

    @PostMapping("/purchase/async") @Operation(summary = "排队购买限时发售商品")
    public CommonResult<String> purchaseAsync(@Valid @RequestBody ReleasePurchaseReqVO reqVO) {
        try {
            return success(kafkaPurchaseQueue.enabled()
                    ? kafkaPurchaseQueue.enqueue(getLoginUserId(), reqVO)
                    : distributedPurchaseQueue.enabled()
                    ? distributedPurchaseQueue.enqueue(getLoginUserId(), reqVO)
                    : purchaseQueue.enqueue(getLoginUserId(), reqVO));
        } catch (cn.iocoder.yudao.module.commerce.service.release.ReleasePurchaseQueue.QueueFullException ex) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), ex);
        } catch (cn.iocoder.yudao.module.commerce.service.release.ReleaseDistributedPurchaseQueue.QueueUnavailableException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }
    }

    @GetMapping("/purchase/async/status") @Operation(summary = "查询排队购买结果")
    public CommonResult<cn.iocoder.yudao.module.commerce.service.release.ReleasePurchaseQueue.Ticket> purchaseAsyncStatus(@RequestParam String ticket) {
        try {
            return success(kafkaPurchaseQueue.enabled()
                    ? kafkaPurchaseQueue.status(ticket, getLoginUserId())
                    : distributedPurchaseQueue.enabled()
                    ? distributedPurchaseQueue.status(ticket, getLoginUserId())
                    : purchaseQueue.status(ticket, getLoginUserId()));
        } catch (cn.iocoder.yudao.module.commerce.service.release.ReleasePurchaseQueue.TicketAccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    @PostMapping("/purchase/async/retry") @Operation(summary = "重放失败的排队购买")
    public CommonResult<Boolean> retryAsync(@RequestParam String ticket) {
        try {
            return success(kafkaPurchaseQueue.enabled()
                    ? kafkaPurchaseQueue.retry(ticket, getLoginUserId())
                    : distributedPurchaseQueue.enabled()
                    ? distributedPurchaseQueue.retry(ticket, getLoginUserId())
                    : false);
        } catch (cn.iocoder.yudao.module.commerce.service.release.ReleasePurchaseQueue.TicketAccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    @GetMapping("/purchase/async/queue-status") @Operation(summary = "查询限时发售队列状态")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:query')")
    public CommonResult<cn.iocoder.yudao.module.commerce.service.release.ReleaseDistributedPurchaseQueue.QueueSnapshot> queueStatus() {
        return success(distributedPurchaseQueue.snapshot());
    }

    @GetMapping("/purchase/async/kafka-status") @Operation(summary = "查询 Kafka 限时发售命令队列状态")
    @PreAuthorize("@ss.hasPermission('commerce:reliability:query')")
    public CommonResult<cn.iocoder.yudao.module.commerce.service.release.ReleaseKafkaPurchaseQueue.Snapshot> kafkaQueueStatus() {
        return success(kafkaPurchaseQueue.snapshot());
    }
}
