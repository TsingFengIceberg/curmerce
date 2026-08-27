package cn.iocoder.yudao.curmerce.cloud.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CoreMediaReferencesReqDTO {
    @NotBlank
    private String businessType;
    @NotBlank
    private String businessId;
    @NotBlank
    private String fieldName;
    private List<String> urls;
}
