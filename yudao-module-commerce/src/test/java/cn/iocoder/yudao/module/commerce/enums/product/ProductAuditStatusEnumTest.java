package cn.iocoder.yudao.module.commerce.enums.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductAuditStatusEnumTest {

    @Test
    void allowsOnlyTheDefinedReviewTransitions() {
        assertTrue(ProductAuditStatusEnum.DRAFT.canTransitionTo(ProductAuditStatusEnum.PENDING));
        assertTrue(ProductAuditStatusEnum.PENDING.canTransitionTo(ProductAuditStatusEnum.APPROVED));
        assertTrue(ProductAuditStatusEnum.PENDING.canTransitionTo(ProductAuditStatusEnum.REJECTED));
        assertTrue(ProductAuditStatusEnum.REJECTED.canTransitionTo(ProductAuditStatusEnum.PENDING));

        assertFalse(ProductAuditStatusEnum.DRAFT.canTransitionTo(ProductAuditStatusEnum.APPROVED));
        assertFalse(ProductAuditStatusEnum.PENDING.canTransitionTo(ProductAuditStatusEnum.DRAFT));
        assertFalse(ProductAuditStatusEnum.APPROVED.canTransitionTo(ProductAuditStatusEnum.REJECTED));
        assertFalse(ProductAuditStatusEnum.REJECTED.canTransitionTo(ProductAuditStatusEnum.REJECTED));
        assertFalse(ProductAuditStatusEnum.DRAFT.canTransitionTo(null));
    }
}
