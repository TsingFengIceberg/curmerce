package cn.iocoder.yudao.module.community.controller.app.comment.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommunityCommentCreateReqVO {
    @NotNull private Long postId;
    private Long parentId;
    @NotBlank @Size(min = 1, max = 2000) private String content;
}
