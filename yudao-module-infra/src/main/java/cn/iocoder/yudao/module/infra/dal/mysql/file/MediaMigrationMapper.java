package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaMigrationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface MediaMigrationMapper extends BaseMapperX<MediaMigrationDO> {

    default MediaMigrationDO selectByFileAndTarget(Long fileId, Long targetConfigId) {
        return selectOne(MediaMigrationDO::getFileId, fileId,
                MediaMigrationDO::getTargetConfigId, targetConfigId);
    }

    @Select("""
            SELECT f.*
            FROM infra_file f
            LEFT JOIN infra_media_migration m
              ON m.file_id = f.id
             AND m.target_config_id = #{targetConfigId}
             AND m.deleted = FALSE
            WHERE f.deleted = FALSE
              AND f.config_id <> #{targetConfigId}
              AND f.asset_key IS NOT NULL
              AND f.asset_status IN (10, 20)
              AND (m.id IS NULL
                   OR m.status = 0
                   OR m.status = 30
                   OR (m.status = 5 AND m.update_time < #{staleBefore})
                   OR (#{switchMetadata} = TRUE AND m.status = 10))
            ORDER BY f.id ASC
            LIMIT #{limit}
            """)
    List<FileDO> selectCandidates(@Param("targetConfigId") Long targetConfigId,
                                  @Param("switchMetadata") boolean switchMetadata,
                                  @Param("staleBefore") LocalDateTime staleBefore,
                                  @Param("limit") int limit);

    @Update("""
            UPDATE infra_media_migration
            SET status = 5,
                attempt_count = attempt_count + 1,
                last_error = NULL,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted = FALSE
              AND (status IN (0, 30)
                   OR (status = 5 AND update_time < #{staleBefore})
                   OR (#{allowCopied} = TRUE AND status = 10))
            """)
    int claim(@Param("id") Long id,
              @Param("allowCopied") boolean allowCopied,
              @Param("staleBefore") LocalDateTime staleBefore);
}
