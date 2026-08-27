package cn.iocoder.yudao.curmerce.cloud.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CorePermissionCheckReqDTO {
    public static final String TYPE_PERMISSION = "permission";
    public static final String TYPE_ROLE = "role";

    @NotNull
    private Long userId;
    @NotEmpty
    private List<String> values;
    @NotEmpty
    private String type;
}
