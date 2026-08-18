package cn.iocoder.yudao.module.commerce.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderShipReqVO;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 商家订单履约")
@RestController
@RequestMapping("/commerce/order")
@Validated
public class OrderController {

    @Resource
    private OrderService orderService;

    @GetMapping("/page-own-pending-shipment")
    @Operation(summary = "查询自己的待发货订单")
    @PreAuthorize("@ss.hasPermission('commerce:order:self-query')")
    public CommonResult<PageResult<MerchantOrderRespVO>> pageOwnPendingShipment(
            @Valid MerchantOrderPageReqVO reqVO) {
        return success(orderService.getOwnPendingShipmentPage(reqVO));
    }

    @PutMapping("/ship-own")
    @Operation(summary = "发货自己的订单")
    @PreAuthorize("@ss.hasPermission('commerce:order:self-ship')")
    public CommonResult<Boolean> shipOwn(@Valid @RequestBody MerchantOrderShipReqVO reqVO) {
        orderService.shipOwnOrder(reqVO);
        return success(true);
    }
}
