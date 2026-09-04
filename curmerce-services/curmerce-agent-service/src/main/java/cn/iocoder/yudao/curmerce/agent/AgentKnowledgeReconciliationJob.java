package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries external vector deletions without requiring an operator request. */
@Component
public class AgentKnowledgeReconciliationJob {
    private final AgentKnowledgeStore store;
    private final MeterRegistry metrics;

    public AgentKnowledgeReconciliationJob(AgentKnowledgeStore store, MeterRegistry metrics) {
        this.store = store;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${curmerce.agent.knowledge-reconcile-ms:60000}")
    public void reconcile() {
        try {
            int repaired = store.reconcileExternalOperations();
            if (repaired > 0) metrics.counter("curmerce.agent.knowledge.external.repair", "result", "repaired").increment(repaired);
        } catch (RuntimeException ignored) {
            metrics.counter("curmerce.agent.knowledge.external.repair", "result", "failed").increment();
        }
    }
}
