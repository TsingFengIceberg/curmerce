package cn.iocoder.yudao.module.commerce.service.reconciliation;

public interface CommerceReconciliationService {

    /**
     * 扫描订单、支付、退款之间的一致性异常，写入 OPEN 台账。
     * 已存在的同范围 OPEN 问题不会重复写入。
     *
     * @param batchSize 每类扫描上限
     * @return 本次新写入的问题数
     */
    int scanAndOpenIssues(int batchSize);

    /**
     * 人工处理完成后将问题台账标记为已解决。
     *
     * @param id 问题台账 ID
     * @return 是否更新成功
     */
    boolean resolveIssue(Long id);

    /** Apply only a safe local state-mirror repair and resolve the issue. */
    boolean repairIssue(Long id);
}
