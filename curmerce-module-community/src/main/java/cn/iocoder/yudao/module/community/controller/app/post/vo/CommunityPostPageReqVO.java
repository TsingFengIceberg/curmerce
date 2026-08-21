package cn.iocoder.yudao.module.community.controller.app.post.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityPostPageReqVO extends PageParam {
    private String keyword;
    private String topicSlug;
}
