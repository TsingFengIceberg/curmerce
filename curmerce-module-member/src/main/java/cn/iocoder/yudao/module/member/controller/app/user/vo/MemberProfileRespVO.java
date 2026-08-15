package cn.iocoder.yudao.module.member.controller.app.user.vo;

import lombok.Data;

@Data
public class MemberProfileRespVO {
    private Long id;
    private String mobile;
    private String nickname;
    private String avatar;
    private String email;
    private Integer sex;
}
