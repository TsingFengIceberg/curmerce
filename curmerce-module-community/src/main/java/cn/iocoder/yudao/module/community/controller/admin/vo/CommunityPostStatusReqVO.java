package cn.iocoder.yudao.module.community.controller.admin.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommunityPostStatusReqVO {
    @NotNull private Long id;
    @NotNull private Integer status;
}
