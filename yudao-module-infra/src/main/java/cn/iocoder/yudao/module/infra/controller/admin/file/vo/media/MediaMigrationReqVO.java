package cn.iocoder.yudao.module.infra.controller.admin.file.vo.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 媒体对象存储迁移 Request VO")
@Data
public class MediaMigrationReqVO {

    @NotNull
    private Long targetConfigId;

    @Min(1)
    @Max(200)
    private Integer batchSize = 50;

    /** Dry-run is the safe default and writes neither objects nor audit rows. */
    private Boolean dryRun = true;

    /** Switching metadata never deletes the source object. */
    private Boolean switchMetadata = false;
}
