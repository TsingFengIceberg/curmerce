package cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class MerchantApproveReqVO {
    @NotNull private Long id;
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9]{4,30}$") private String username;
    @NotBlank @Size(min = 2, max = 30) private String nickname;
    @NotBlank @Size(min = 8, max = 64) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude private String password;
}
