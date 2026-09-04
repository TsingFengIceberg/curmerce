package cn.iocoder.yudao.module.commerce.service.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseQueueFailureInjectorTest {
    @Test
    void disabledInjectorIsInert() {
        ReleaseQueueFailureInjector injector = new ReleaseQueueFailureInjector("NONE", true);
        assertDoesNotThrow(() -> injector.failIfConfigured(ReleaseQueueFailureInjector.Point.ENQUEUE));
    }

    @Test
    void failOnceInjectorOnlyFailsAtSelectedPoint() {
        ReleaseQueueFailureInjector injector = new ReleaseQueueFailureInjector("AFTER_COMMIT_STATUS", true);
        assertDoesNotThrow(() -> injector.failIfConfigured(ReleaseQueueFailureInjector.Point.ENQUEUE));
        assertThrows(ReleaseQueueFailureInjector.InjectedReleaseQueueFailure.class,
                () -> injector.failIfConfigured(ReleaseQueueFailureInjector.Point.AFTER_COMMIT_STATUS));
        assertDoesNotThrow(() -> injector.failIfConfigured(ReleaseQueueFailureInjector.Point.AFTER_COMMIT_STATUS));
    }
}
