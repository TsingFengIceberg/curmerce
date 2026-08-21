package cn.iocoder.yudao.module.community.enums;

public enum CommunityPostStatusEnum {
    DRAFT(0), PUBLISHED(1), HIDDEN(2);
    private final int status;
    CommunityPostStatusEnum(int status) { this.status = status; }
    public int getStatus() { return status; }
}
