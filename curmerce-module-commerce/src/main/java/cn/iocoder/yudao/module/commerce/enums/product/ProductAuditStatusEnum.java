package cn.iocoder.yudao.module.commerce.enums.product;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Product platform-review state.
 */
@Getter
@AllArgsConstructor
public enum ProductAuditStatusEnum implements ArrayValuable<Integer> {

    DRAFT(0, "草稿"),
    PENDING(1, "待审核"),
    APPROVED(2, "审核通过"),
    REJECTED(3, "审核拒绝");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(ProductAuditStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    /**
     * Whether the product may enter {@code target} from this review state.
     * Same-state updates are intentionally not transitions; idempotency is an
     * application-service concern.
     */
    public boolean canTransitionTo(ProductAuditStatusEnum target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == PENDING;
            case PENDING -> target == APPROVED || target == REJECTED;
            case APPROVED -> false;
            case REJECTED -> target == PENDING;
        };
    }
}
