package cn.iocoder.yudao.server.controller.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalRequestGuardTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    void check_acceptsOnlyExactStrongToken() {
        InternalRequestGuard guard = new InternalRequestGuard(TOKEN);

        assertDoesNotThrow(() -> guard.check(TOKEN));
        assertThrows(AccessDeniedException.class, () -> guard.check("wrong"));
        assertThrows(AccessDeniedException.class, () -> guard.check(null));
    }

    @Test
    void check_rejectsWeakConfigurationEvenWhenItMatches() {
        InternalRequestGuard guard = new InternalRequestGuard("short");

        assertThrows(AccessDeniedException.class, () -> guard.check("short"));
    }
}
