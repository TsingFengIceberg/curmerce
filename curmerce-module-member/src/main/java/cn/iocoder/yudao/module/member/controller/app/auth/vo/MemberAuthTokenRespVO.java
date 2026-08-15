package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MemberAuthTokenRespVO {
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresTime;
}
