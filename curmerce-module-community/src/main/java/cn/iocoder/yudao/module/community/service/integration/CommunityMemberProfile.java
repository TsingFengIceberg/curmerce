package cn.iocoder.yudao.module.community.service.integration;

import lombok.Data;

@Data
public class CommunityMemberProfile {
    private Long id;
    private String nickname;
    private String avatar;
    private Integer status;
}
