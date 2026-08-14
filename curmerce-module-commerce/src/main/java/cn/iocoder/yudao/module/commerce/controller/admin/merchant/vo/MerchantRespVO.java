package cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantRespVO {
    private Long id;
    private String name;
    private String code;
    private String contactName;
    private String contactMobile;
    private String defaultStoreName;
    private String defaultStoreCode;
    private Integer status;
    private Long ownerUserId;
    private Long reviewerUserId;
    private LocalDateTime reviewTime;
    private String rejectReason;
    private LocalDateTime createTime;
}
