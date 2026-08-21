package cn.iocoder.yudao.module.community.enums;

public enum CommunityReactionTypeEnum {
    LIKE(1), FAVORITE(2);
    private final int type;
    CommunityReactionTypeEnum(int type) { this.type = type; }
    public int getType() { return type; }
}
