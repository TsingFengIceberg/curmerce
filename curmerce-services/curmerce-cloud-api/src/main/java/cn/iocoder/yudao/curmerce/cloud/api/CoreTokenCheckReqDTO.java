package cn.iocoder.yudao.curmerce.cloud.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CoreTokenCheckReqDTO {
    @NotBlank
    private String token;
}
