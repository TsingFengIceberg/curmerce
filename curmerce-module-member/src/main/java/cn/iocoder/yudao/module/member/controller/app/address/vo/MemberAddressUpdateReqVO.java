package cn.iocoder.yudao.module.member.controller.app.address.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MemberAddressUpdateReqVO extends MemberAddressBaseVO {
    @NotNull(message = "地址编号不能为空")
    private Long id;
}
