package cn.iocoder.yudao.module.community.controller.app.post.vo;

import lombok.Data;

@Data
public class CommunityTopicRespVO {
    private Long id;
    private String name;
    private String slug;
    private Long postCount;
}
