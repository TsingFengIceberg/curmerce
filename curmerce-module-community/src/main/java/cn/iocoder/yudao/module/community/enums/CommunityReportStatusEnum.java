package cn.iocoder.yudao.module.community.enums;

public enum CommunityReportStatusEnum {
    PENDING(0), RESOLVED(1), REJECTED(2);
    private final int status;
    CommunityReportStatusEnum(int status) { this.status = status; }
    public int getStatus() { return status; }
}
