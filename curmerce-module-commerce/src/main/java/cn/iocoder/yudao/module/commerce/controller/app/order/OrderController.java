package cn.iocoder.yudao.module.commerce.controller.app.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.*;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 买家订单")
@RestController
@RequestMapping("/commerce/order")
@Validated
public class OrderController {
    @Resource
    private OrderService orderService;

    @PostMapping("/create")
    @Operation(summary = "创建订单", description = "只使用当前买家已选购物车项；同一买家使用相同幂等键重试会返回原订单")
    public CommonResult<OrderCreateRespVO> create(@Valid @RequestBody OrderCreateReqVO reqVO,
                                                    @Parameter(description = "同一买家同一次下单重试必须复用")
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return success(orderService.createOrder(getLoginUserId(), reqVO.getAddressId(), idempotencyKey));
    }

    @GetMapping("/page")
    @Operation(summary = "查询我的订单")
    public CommonResult<PageResult<OrderSummaryRespVO>> page(@Valid OrderPageReqVO reqVO) {
        return success(orderService.getOrderPage(getLoginUserId(), reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "查询我的订单详情")
    public CommonResult<OrderDetailRespVO> get(@RequestParam Long id) {
        return success(orderService.getOrder(getLoginUserId(), id));
    }

    @PutMapping("/confirm-receipt")
    @Operation(summary = "确认收货")
    public CommonResult<Boolean> confirmReceipt(@Valid @RequestBody OrderConfirmReceiptReqVO reqVO) {
        orderService.confirmReceipt(getLoginUserId(), reqVO.getId());
        return success(true);
    }
}
