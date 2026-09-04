package cn.iocoder.yudao.module.commerce.service.release;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in, fail-once hooks for local limited-release reliability drills. */
@Component
public class ReleaseQueueFailureInjector {
    private final Point point;
    private final boolean failOnce;
    private final AtomicBoolean fired = new AtomicBoolean();

    public ReleaseQueueFailureInjector(
            @Value("${curmerce.release.fault-injection-point:NONE}") String configuredPoint,
            @Value("${curmerce.release.fault-injection-fail-once:true}") boolean failOnce) {
        Point parsed;
        try {
            parsed = Point.valueOf(configuredPoint == null ? "NONE"
                    : configuredPoint.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            parsed = Point.NONE;
        }
        this.point = parsed;
        this.failOnce = failOnce;
    }

    public void failIfConfigured(Point current) {
        if (point != current || (failOnce && !fired.compareAndSet(false, true))) return;
        throw new InjectedReleaseQueueFailure("限时发售故障演练: " + current.name());
    }

    public Point point() { return point; }

    public enum Point { NONE, ENQUEUE, BEFORE_PURCHASE, AFTER_COMMIT_STATUS, ACK, DEAD_LETTER }

    public static class InjectedReleaseQueueFailure extends RuntimeException {
        public InjectedReleaseQueueFailure(String message) { super(message); }
    }
}
