package cn.iocoder.yudao.module.community.controller.app.interaction.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommunityFollowReqVO {
    @NotNull private Long userId;
    @NotNull private Boolean active;
}
