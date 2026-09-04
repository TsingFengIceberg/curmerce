package cn.iocoder.yudao.module.commerce.controller.app.auction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.auction.vo.*;
import cn.iocoder.yudao.module.commerce.service.auction.AuctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import cn.iocoder.yudao.module.commerce.service.auction.AuctionEventBroadcaster;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 拍卖")
@RestController("commerceAppAuctionController")
@RequestMapping("/commerce/auction")
@Validated
public class AuctionController {
    @Resource private AuctionService auctionService;
    @Resource private AuctionEventBroadcaster eventBroadcaster;
    @GetMapping(value = "/events", produces = "text/event-stream") @PermitAll @Operation(summary = "订阅拍卖实时事件")
    public SseEmitter events(@RequestParam Long sessionId) { return eventBroadcaster.subscribe(sessionId); }
    @GetMapping("/page") @PermitAll @Operation(summary = "查询公开拍卖场次")
    public CommonResult<PageResult<AuctionRespVO>> page(@Valid AuctionPageReqVO reqVO) { return success(auctionService.getPublicPage(reqVO)); }
    @GetMapping("/get") @PermitAll @Operation(summary = "查询拍卖详情")
    public CommonResult<AuctionRespVO> get(@RequestParam Long id) { return success(auctionService.get(id, true)); }
    @GetMapping("/bid-page") @PermitAll @Operation(summary = "查询公开竞价记录")
    public CommonResult<PageResult<AuctionBidRespVO>> bidPage(@Valid AuctionBidPageReqVO reqVO) {
        return success(auctionService.getBidPage(reqVO, getLoginUserId()));
    }
    @PostMapping("/bid") @Operation(summary = "提交拍卖出价")
    public CommonResult<Long> bid(@Valid @RequestBody AuctionBidReqVO reqVO) { return success(auctionService.bid(getLoginUserId(), reqVO)); }
    @PostMapping("/settle") @Operation(summary = "拍卖胜者创建订单")
    public CommonResult<Long> settle(@Valid @RequestBody AuctionSettleReqVO reqVO) {
        return success(auctionService.settle(getLoginUserId(), reqVO.getSessionId(), reqVO.getAddressId()));
    }
}
