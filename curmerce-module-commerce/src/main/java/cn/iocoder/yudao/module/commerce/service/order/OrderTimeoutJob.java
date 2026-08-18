package cn.iocoder.yudao.module.commerce.service.order;

import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderTimeoutJob {
    @Resource
    private OrderService orderService;

    @Scheduled(fixedDelayString = "${curmerce.order.pending-payment-close-delay-ms:60000}")
    public void closeExpiredPendingPaymentOrders() {
        orderService.closeExpiredPendingPaymentOrders(LocalDateTime.now(), 100);
    }
}
