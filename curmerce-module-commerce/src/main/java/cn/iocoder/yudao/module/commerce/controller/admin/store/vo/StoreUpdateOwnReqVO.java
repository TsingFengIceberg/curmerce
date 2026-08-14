package cn.iocoder.yudao.module.commerce.controller.admin.store.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoreUpdateOwnReqVO {
    @NotBlank @Size(min = 2, max = 64) private String name;
    @Size(max = 500) private String description;
    @NotBlank @Size(min = 2, max = 30) private String contactName;
    @NotBlank @Mobile private String contactMobile;
}
