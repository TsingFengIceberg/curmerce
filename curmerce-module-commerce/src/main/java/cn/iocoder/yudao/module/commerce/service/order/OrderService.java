package cn.iocoder.yudao.module.commerce.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderShipReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.CommerceOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.*;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.PersonalSellerOrderRespVO;

import java.time.LocalDateTime;

public interface OrderService {
    OrderCreateRespVO createOrder(Long userId, Long addressId, String idempotencyKey);
    OrderCreateRespVO createReleaseOrder(Long userId, Long addressId, Long productId, Long skuId,
                                          Long amount, Integer quantity, String idempotencyKey);
    OrderCreateRespVO createAuctionOrder(Long userId, Long addressId, Long productId, Long skuId, Long amount, String idempotencyKey);
    PageResult<OrderSummaryRespVO> getOrderPage(Long userId, OrderPageReqVO reqVO);
    OrderDetailRespVO getOrder(Long userId, Long id);
    void confirmReceipt(Long userId, Long id);
    void cancelOrder(Long userId, Long id);
    int closeExpiredPendingPaymentOrders(LocalDateTime cutoffTime, int batchSize);
    PageResult<MerchantOrderRespVO> getOwnPendingShipmentPage(MerchantOrderPageReqVO reqVO);
    PageResult<MerchantOrderRespVO> getAdminOrderPage(CommerceOrderPageReqVO reqVO);
    void shipOwnOrder(MerchantOrderShipReqVO reqVO);
    PageResult<PersonalSellerOrderRespVO> getOwnPersonalPendingShipmentPage(Long sellerUserId, MerchantOrderPageReqVO reqVO);
    void shipPersonalOrder(Long sellerUserId, MerchantOrderShipReqVO reqVO);
}
