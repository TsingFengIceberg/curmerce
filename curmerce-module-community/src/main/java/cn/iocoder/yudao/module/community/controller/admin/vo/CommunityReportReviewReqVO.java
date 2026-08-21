package cn.iocoder.yudao.module.community.controller.admin.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommunityReportReviewReqVO {
    @NotNull private Long id;
    @NotNull private Integer status;
    @Size(max = 500) private String remark;
}
