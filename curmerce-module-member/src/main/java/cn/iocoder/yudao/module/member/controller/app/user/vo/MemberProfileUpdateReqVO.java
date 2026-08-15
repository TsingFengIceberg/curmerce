package cn.iocoder.yudao.module.member.controller.app.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class MemberProfileUpdateReqVO {
    @NotBlank(message = "昵称不能为空") @Length(min = 2, max = 30)
    private String nickname;
    @Length(max = 1024)
    private String avatar;
    @Email(message = "邮箱格式不正确") @Length(max = 254)
    private String email;
    @Schema(description = "性别：0未知、1男、2女")
    private Integer sex;
}
