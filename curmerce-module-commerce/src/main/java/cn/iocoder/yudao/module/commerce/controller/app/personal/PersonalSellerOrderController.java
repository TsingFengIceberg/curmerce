package cn.iocoder.yudao.module.commerce.controller.app.personal;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderShipReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.PersonalSellerOrderRespVO;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 个人卖家订单履约")
@RestController
@RequestMapping("/commerce/personal-seller/order")
@Validated
public class PersonalSellerOrderController {
    @Resource private OrderService orderService;

    @GetMapping("/page-pending-shipment")
    @Operation(summary = "查询自己的个人商品待发货订单")
    public CommonResult<PageResult<PersonalSellerOrderRespVO>> pagePendingShipment(
            @Valid MerchantOrderPageReqVO reqVO) {
        return success(orderService.getOwnPersonalPendingShipmentPage(getLoginUserId(), reqVO));
    }

    @PutMapping("/ship")
    @Operation(summary = "发货自己的个人商品订单")
    public CommonResult<Boolean> ship(@Valid @RequestBody MerchantOrderShipReqVO reqVO) {
        orderService.shipPersonalOrder(getLoginUserId(), reqVO);
        return success(true);
    }
}
