package cn.iocoder.yudao.module.system.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import cn.iocoder.yudao.framework.common.validation.Mobile;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class AdminUserProvisionReqDTO {

    @NotBlank(message = "用户账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,30}$", message = "用户账号格式不正确")
    private String username;

    @NotBlank(message = "用户昵称不能为空")
    @Size(min = 2, max = 30, message = "用户昵称长度为 2-30 个字符")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @Length(min = 8, max = 64, message = "密码长度为 8-64 位")
    private String password;

    @Mobile
    @Size(max = 20, message = "手机号长度不能超过 20 个字符")
    private String mobile;

    @NotBlank(message = "角色标识不能为空")
    private String roleCode;
}
