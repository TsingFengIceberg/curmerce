package cn.iocoder.yudao.module.commerce.enums.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommerceReconciliationIssueTypeEnum {
    PAYMENT_ORDER_STATE_MISMATCH("支付成功但订单状态异常"),
    ORDER_WITHOUT_SUCCESS_PAYMENT("订单已履约但没有成功支付单"),
    REFUND_ORDER_STATUS_MISMATCH("退款与订单售后状态不一致");

    private final String description;
}
