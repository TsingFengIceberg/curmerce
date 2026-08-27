package cn.iocoder.yudao.module.infra.controller.admin.file.vo.media;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理后台 - 媒体对象存储迁移 Response VO")
public record MediaMigrationRespVO(boolean dryRun, int candidates, long candidateBytes,
                                   int copied, int switched, int skipped, int failed) {
}
