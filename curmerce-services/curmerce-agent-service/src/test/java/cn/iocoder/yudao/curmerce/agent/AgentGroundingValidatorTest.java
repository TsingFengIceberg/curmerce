package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentGroundingValidatorTest {
    private final AgentGroundingValidator validator = new AgentGroundingValidator();

    @Test
    void acceptsClaimsPresentInEvidence() {
        assertTrue(validator.validate("商品价格是 ¥12.50", "商品价格 ¥12.50").isEmpty());
    }

    @Test
    void flagsUnsupportedMoneyAndOrderClaims() {
        var warnings = validator.validate("订单号 9001 的价格是 ¥99.00", "商品列表为空");
        assertEquals(2, warnings.size());
    }

    @Test
    void acceptsSupportedInventoryButFlagsUnsupportedInventory() {
        assertTrue(validator.validate("该 SKU 库存为 3 件", "SKU: 库存 3 件").isEmpty());
        assertTrue(validator.validate("该 SKU 库存为 9 件", "SKU: 库存 3 件").stream()
                .anyMatch(value -> value.contains("库存")));
    }
}
