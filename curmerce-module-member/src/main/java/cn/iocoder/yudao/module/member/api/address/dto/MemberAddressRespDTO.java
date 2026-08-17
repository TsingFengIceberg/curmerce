package cn.iocoder.yudao.module.member.api.address.dto;

import lombok.Data;

/**
 * Stable address snapshot contract exposed to other Curmerce modules.
 */
@Data
public class MemberAddressRespDTO {
    private Long id;
    private Long userId;
    private String name;
    private String mobile;
    private Integer areaId;
    private String areaName;
    private String detailAddress;
}
