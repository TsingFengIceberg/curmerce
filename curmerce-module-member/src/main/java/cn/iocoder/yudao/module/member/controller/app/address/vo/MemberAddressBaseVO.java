package cn.iocoder.yudao.module.member.controller.app.address.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class MemberAddressBaseVO {
    @NotBlank(message = "收件人不能为空") @Length(max = 30)
    private String name;
    @NotBlank(message = "手机号不能为空") @Mobile
    private String mobile;
    @NotNull(message = "地区不能为空")
    private Integer areaId;
    @NotBlank(message = "详细地址不能为空") @Length(max = 255)
    private String detailAddress;
    @NotNull(message = "是否默认地址不能为空")
    private Boolean defaultStatus;
}
