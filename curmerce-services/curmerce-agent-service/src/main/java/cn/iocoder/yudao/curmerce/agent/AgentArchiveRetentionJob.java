package cn.iocoder.yudao.curmerce.agent;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps optional JDBC audit and usage archives bounded during quiet periods. */
@Component
public class AgentArchiveRetentionJob {
    private final AgentAuditJdbcArchive audit;
    private final AgentUsageJdbcArchive usage;

    public AgentArchiveRetentionJob(
            org.springframework.beans.factory.ObjectProvider<AgentAuditJdbcArchive> auditProvider,
            org.springframework.beans.factory.ObjectProvider<AgentUsageJdbcArchive> usageProvider) {
        this.audit = auditProvider.getIfAvailable();
        this.usage = usageProvider.getIfAvailable();
    }

    @Scheduled(fixedDelayString = "${curmerce.agent.audit-retention-cleanup-ms:3600000}")
    public void purge() {
        if (audit != null) try { audit.purgeExpired(); } catch (RuntimeException ignored) { }
        if (usage != null) try { usage.purgeExpired(); } catch (RuntimeException ignored) { }
    }
}
