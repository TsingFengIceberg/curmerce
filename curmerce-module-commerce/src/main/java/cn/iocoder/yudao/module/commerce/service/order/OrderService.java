package cn.iocoder.yudao.module.commerce.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.*;

public interface OrderService {
    OrderCreateRespVO createOrder(Long userId, Long addressId, String idempotencyKey);
    PageResult<OrderSummaryRespVO> getOrderPage(Long userId, OrderPageReqVO reqVO);
    OrderDetailRespVO getOrder(Long userId, Long id);
}
