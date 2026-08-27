package cn.iocoder.yudao.module.infra.service.file;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MediaMetrics {

    private final DistributionSummary uploadBytes;
    private final Counter uploaded;
    private final Counter deduplicated;
    private final Counter rejected;
    private final Counter delivered;
    private final Counter variantsCreated;
    private final Counter variantsFailed;
    private final Counter orphansDeleted;
    private final Counter directTickets;
    private final Counter directFinalized;
    private final Counter directRejected;
    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> moderated = new ConcurrentHashMap<>();

    public MediaMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable(SimpleMeterRegistry::new);
        uploadBytes = DistributionSummary.builder("curmerce.media.upload.bytes")
                .baseUnit("bytes").register(this.registry);
        uploaded = this.registry.counter("curmerce.media.upload.total", "result", "stored");
        deduplicated = this.registry.counter("curmerce.media.upload.total", "result", "deduplicated");
        rejected = this.registry.counter("curmerce.media.upload.total", "result", "rejected");
        delivered = this.registry.counter("curmerce.media.delivery.total");
        variantsCreated = this.registry.counter("curmerce.media.variant.total", "result", "created");
        variantsFailed = this.registry.counter("curmerce.media.variant.total", "result", "failed");
        orphansDeleted = this.registry.counter("curmerce.media.orphan.total", "result", "deleted");
        directTickets = this.registry.counter("curmerce.media.direct.total", "result", "ticket-issued");
        directFinalized = this.registry.counter("curmerce.media.direct.total", "result", "finalized");
        directRejected = this.registry.counter("curmerce.media.direct.total", "result", "rejected");
    }

    public void stored(long bytes) { uploaded.increment(); uploadBytes.record(bytes); }
    public void deduplicated(long bytes) { deduplicated.increment(); uploadBytes.record(bytes); }
    public void rejected() { rejected.increment(); }
    public void delivered() { delivered.increment(); }
    public void variantCreated() { variantsCreated.increment(); }
    public void variantFailed() { variantsFailed.increment(); }
    public void orphansDeleted(int count) { orphansDeleted.increment(count); }
    public void directTicketIssued() { directTickets.increment(); }
    public void directFinalized() { directFinalized.increment(); }
    public void directRejected() { directRejected.increment(); }
    public void moderated(String result) {
        moderated.computeIfAbsent(result,
                key -> registry.counter("curmerce.media.moderation.total", "result", key)).increment();
    }
}
