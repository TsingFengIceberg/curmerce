package cn.iocoder.yudao.module.infra.controller.admin.file.vo.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 媒体治理操作 Request VO")
@Data
public class MediaModerationReqVO {

    @Schema(description = "操作原因")
    @Size(max = 500)
    private String reason;
}
