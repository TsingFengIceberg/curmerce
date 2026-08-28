package cn.iocoder.yudao.module.commerce.enums.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommerceOutboxEventTypeEnum {
    ORDER_PAID("commerce_order", "订单支付成功"),
    ORDER_SHIPPED("commerce_order", "商家发货"),
    ORDER_COMPLETED("commerce_order", "买家确认收货"),
    ORDER_CANCELED("commerce_order", "订单取消或超时关闭"),
    INVENTORY_RESERVED("commerce_inventory", "库存预留"),
    INVENTORY_RELEASED("commerce_inventory", "库存释放"),
    PRODUCT_CHANGED("commerce_product", "商品状态或资料变更"),
    REFUND_SUCCESS("commerce_refund", "退款成功"),
    REFUND_FAILED("commerce_refund", "退款失败");

    /** 聚合类型，用于事件消费端定位业务对象。 */
    private final String aggregateType;
    /** 事件含义说明。 */
    private final String description;
}
