package cn.iocoder.yudao.module.commerce.service.order;

import cn.iocoder.yudao.module.commerce.service.auction.AuctionService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderTimeoutJob {
    @Resource
    private OrderService orderService;
    @Resource
    private AuctionService auctionService;

    @Scheduled(fixedDelayString = "${curmerce.order.pending-payment-close-delay-ms:60000}")
    public void closeExpiredPendingPaymentOrders() {
        LocalDateTime now = LocalDateTime.now();
        orderService.closeExpiredPendingPaymentOrders(now, 100);
        auctionService.markUnpaidSettlementsFailed(now, 100);
    }
}
