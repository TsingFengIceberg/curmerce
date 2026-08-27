package cn.iocoder.yudao.module.infra.dal.dataobject.file;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("infra_media_migration")
@KeySequence("infra_media_migration_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaMigrationDO extends BaseDO {
    private Long id;
    private Long fileId;
    private Long sourceConfigId;
    private Long targetConfigId;
    private String sourcePath;
    private String targetPath;
    private String sha256;
    private Integer status;
    private Integer attemptCount;
    private String lastError;
    private LocalDateTime copiedAt;
    private LocalDateTime switchedAt;
}
