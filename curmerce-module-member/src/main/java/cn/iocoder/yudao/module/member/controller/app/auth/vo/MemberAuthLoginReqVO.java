package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.Length;

@Data
public class MemberAuthLoginReqVO {
    @NotBlank(message = "手机号不能为空") @Mobile
    private String mobile;
    @NotBlank(message = "密码不能为空") @Length(min = 8, max = 64) @ToString.Exclude
    private String password;
}
