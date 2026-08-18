package cn.iocoder.yudao.module.commerce.controller.admin.order.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantOrderShipReqVO {
    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 64)
    private String logisticsCompany;

    @NotBlank
    @Size(max = 64)
    private String trackingNo;
}
