package cn.iocoder.yudao.module.community.controller.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommunityReportRespVO {
    private Long id;
    private Long postId;
    private String postTitle;
    private String postContent;
    private List<String> postMediaUrls;
    private Long postAuthorUserId;
    private String postAuthorNickname;
    private Long reporterUserId;
    private String reporterNickname;
    private String reason;
    private Integer status;
    private Long reviewerUserId;
    private String reviewRemark;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
