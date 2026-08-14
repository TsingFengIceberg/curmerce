package cn.iocoder.yudao.module.commerce.controller.admin.store.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoreRespVO {
    private Long id;
    private Long merchantId;
    private String name;
    private String code;
    private String description;
    private String contactName;
    private String contactMobile;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
