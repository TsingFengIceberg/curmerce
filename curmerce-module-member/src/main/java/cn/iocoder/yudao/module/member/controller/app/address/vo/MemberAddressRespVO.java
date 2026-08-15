package cn.iocoder.yudao.module.member.controller.app.address.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MemberAddressRespVO extends MemberAddressBaseVO {
    private Long id;
    private String areaName;
}
