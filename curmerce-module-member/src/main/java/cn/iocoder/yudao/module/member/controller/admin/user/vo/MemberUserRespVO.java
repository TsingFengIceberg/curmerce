package cn.iocoder.yudao.module.member.controller.admin.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberUserRespVO {
    private Long id;
    private String mobile;
    private String nickname;
    private String avatar;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
}
