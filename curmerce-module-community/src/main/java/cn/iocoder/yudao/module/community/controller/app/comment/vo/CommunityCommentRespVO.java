package cn.iocoder.yudao.module.community.controller.app.comment.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommunityCommentRespVO {
    private Long id;
    private Long postId;
    private Long parentId;
    private Long authorUserId;
    private String authorNickname;
    private String authorAvatar;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
}
