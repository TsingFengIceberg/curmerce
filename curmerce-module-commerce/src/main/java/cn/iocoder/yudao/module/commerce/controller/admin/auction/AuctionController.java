package cn.iocoder.yudao.module.commerce.controller.admin.auction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.auction.vo.*;
import cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionRespVO;
import cn.iocoder.yudao.module.commerce.service.auction.AuctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 拍卖")
@RestController
@RequestMapping("/commerce/auction")
@Validated
public class AuctionController {
    @Resource private AuctionService auctionService;
    @PostMapping("/create") @Operation(summary = "创建拍卖场次")
    @PreAuthorize("@ss.hasPermission('commerce:auction:create')")
    public CommonResult<Long> create(@Valid @RequestBody AuctionCreateReqVO reqVO) { return success(auctionService.create(reqVO)); }
    @PutMapping("/update") @Operation(summary = "修改拍卖草稿")
    @PreAuthorize("@ss.hasPermission('commerce:auction:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody AuctionUpdateReqVO reqVO) {
        auctionService.update(reqVO);
        return success(true);
    }
    @GetMapping("/get") @Operation(summary = "查询我的拍卖详情")
    @PreAuthorize("@ss.hasPermission('commerce:auction:query')")
    public CommonResult<AuctionRespVO> get(@RequestParam Long id) { return success(auctionService.getOwn(id)); }
    @GetMapping("/page") @Operation(summary = "查询我的拍卖场次")
    @PreAuthorize("@ss.hasPermission('commerce:auction:query')")
    public CommonResult<PageResult<AuctionRespVO>> page(@Valid AuctionPageReqVO reqVO) { return success(auctionService.getOwnPage(reqVO)); }
    @PutMapping("/publish") @PreAuthorize("@ss.hasPermission('commerce:auction:update')")
    public CommonResult<Boolean> publish(@RequestParam Long id) { auctionService.publish(id); return success(true); }
    @PutMapping("/cancel") @PreAuthorize("@ss.hasPermission('commerce:auction:update')")
    public CommonResult<Boolean> cancel(@RequestParam Long id) { auctionService.cancel(id); return success(true); }
    @PutMapping("/end") @PreAuthorize("@ss.hasPermission('commerce:auction:update')")
    public CommonResult<Boolean> end(@RequestParam Long id) { auctionService.end(id); return success(true); }
}
