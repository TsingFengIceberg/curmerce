package cn.iocoder.yudao.module.commerce.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderShipReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.*;

import java.time.LocalDateTime;

public interface OrderService {
    OrderCreateRespVO createOrder(Long userId, Long addressId, String idempotencyKey);
    PageResult<OrderSummaryRespVO> getOrderPage(Long userId, OrderPageReqVO reqVO);
    OrderDetailRespVO getOrder(Long userId, Long id);
    void confirmReceipt(Long userId, Long id);
    void cancelOrder(Long userId, Long id);
    int closeExpiredPendingPaymentOrders(LocalDateTime cutoffTime, int batchSize);
    PageResult<MerchantOrderRespVO> getOwnPendingShipmentPage(MerchantOrderPageReqVO reqVO);
    void shipOwnOrder(MerchantOrderShipReqVO reqVO);
}
