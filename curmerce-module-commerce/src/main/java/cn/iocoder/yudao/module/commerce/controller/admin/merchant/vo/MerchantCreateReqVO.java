package cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantCreateReqVO {
    @NotBlank @Size(min = 2, max = 64) private String name;
    @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{2,31}$") private String code;
    @NotBlank @Size(min = 2, max = 30) private String contactName;
    @NotBlank @Mobile private String contactMobile;
    @NotBlank @Size(min = 2, max = 64) private String defaultStoreName;
    @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{2,31}$") private String defaultStoreCode;
}
