package cn.iocoder.yudao.module.community.controller.app.post.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Positive;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityPostPageReqVO extends PageParam {
    private String keyword;
    private String topicSlug;
    @Positive
    private Long productId;
}
