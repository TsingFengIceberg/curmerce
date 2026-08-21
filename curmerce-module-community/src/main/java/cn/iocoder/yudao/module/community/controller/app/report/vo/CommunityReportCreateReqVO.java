package cn.iocoder.yudao.module.community.controller.app.report.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommunityReportCreateReqVO {
    @NotNull private Long postId;
    @NotBlank @Size(max = 500) private String reason;
}
