package cn.iocoder.yudao.module.commerce.service.reconciliation;

import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommerceReconciliationJob {

    @Resource
    private CommerceReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${curmerce.reconciliation.scan-delay-ms:60000}")
    public void scanConsistencyIssues() {
        reconciliationService.scanAndOpenIssues(100);
    }
}
