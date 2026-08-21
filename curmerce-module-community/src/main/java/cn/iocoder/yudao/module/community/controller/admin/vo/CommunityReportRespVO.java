package cn.iocoder.yudao.module.community.controller.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommunityReportRespVO {
    private Long id;
    private Long postId;
    private Long reporterUserId;
    private String reason;
    private Integer status;
    private Long reviewerUserId;
    private String reviewRemark;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
