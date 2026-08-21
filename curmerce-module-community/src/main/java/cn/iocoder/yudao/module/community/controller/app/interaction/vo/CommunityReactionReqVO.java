package cn.iocoder.yudao.module.community.controller.app.interaction.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommunityReactionReqVO {
    @NotNull private Long postId;
    @NotNull private Integer type;
    @NotNull private Boolean active;
}
