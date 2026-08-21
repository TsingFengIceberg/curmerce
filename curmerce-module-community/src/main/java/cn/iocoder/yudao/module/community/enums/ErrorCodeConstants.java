package cn.iocoder.yudao.module.community.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {
    ErrorCode POST_NOT_FOUND = new ErrorCode(1_024_001_001, "帖子不存在或不可见");
    ErrorCode POST_STATE_INVALID = new ErrorCode(1_024_001_002, "帖子当前状态不允许该操作");
    ErrorCode POST_CONTENT_INVALID = new ErrorCode(1_024_001_003, "帖子内容无效");
    ErrorCode POST_PRODUCT_INVALID = new ErrorCode(1_024_001_004, "关联商品不存在或不可见");
    ErrorCode TOPIC_INVALID = new ErrorCode(1_024_001_005, "话题名称无效");
    ErrorCode COMMENT_NOT_FOUND = new ErrorCode(1_024_001_006, "评论不存在");
    ErrorCode COMMENT_PARENT_INVALID = new ErrorCode(1_024_001_007, "评论回复目标无效");
    ErrorCode REACTION_TYPE_INVALID = new ErrorCode(1_024_001_008, "互动类型无效");
    ErrorCode FOLLOW_SELF_INVALID = new ErrorCode(1_024_001_009, "不能关注自己");
    ErrorCode REPORT_INVALID = new ErrorCode(1_024_001_010, "举报内容无效");
    ErrorCode REPORT_STATE_INVALID = new ErrorCode(1_024_001_011, "举报状态无效");
}
