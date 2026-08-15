package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.Length;

@Schema(description = "Curmerce 买家注册 Request VO")
@Data
public class MemberAuthRegisterReqVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "手机号不能为空") @Mobile
    private String mobile;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空") @Length(min = 8, max = 64, message = "密码长度为 8-64 位")
    @ToString.Exclude
    private String password;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "昵称不能为空") @Length(min = 2, max = 30, message = "昵称长度为 2-30 位")
    private String nickname;
}
