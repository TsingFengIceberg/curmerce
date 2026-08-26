package cn.iocoder.yudao.module.member.api.user.dto;

import lombok.Data;

@Data
public class MemberUserRespDTO {
    private Long id;
    private String mobile;
    private String nickname;
    private String avatar;
    private String email;
    private Integer status;
}
