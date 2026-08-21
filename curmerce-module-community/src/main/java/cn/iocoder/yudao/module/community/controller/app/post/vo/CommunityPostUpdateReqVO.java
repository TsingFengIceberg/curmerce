package cn.iocoder.yudao.module.community.controller.app.post.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityPostUpdateReqVO extends CommunityPostCreateReqVO {
    @NotNull private Long id;
}
