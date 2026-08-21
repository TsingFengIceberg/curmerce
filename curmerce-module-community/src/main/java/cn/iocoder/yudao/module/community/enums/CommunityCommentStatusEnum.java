package cn.iocoder.yudao.module.community.enums;

public enum CommunityCommentStatusEnum {
    VISIBLE(0), HIDDEN(1);
    private final int status;
    CommunityCommentStatusEnum(int status) { this.status = status; }
    public int getStatus() { return status; }
}
