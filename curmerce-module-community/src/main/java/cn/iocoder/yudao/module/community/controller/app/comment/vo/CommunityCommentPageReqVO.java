package cn.iocoder.yudao.module.community.controller.app.comment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityCommentPageReqVO extends PageParam {
    @NotNull private Long postId;
}
