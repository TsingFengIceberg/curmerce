package cn.iocoder.yudao.curmerce.cloud.api;

import lombok.Data;

@Data
public class CoreMemberUserRespDTO {
    private Long id;
    private String mobile;
    private String nickname;
    private String avatar;
    private String email;
    private Integer status;
}
