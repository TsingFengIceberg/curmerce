package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaUploadQuotaDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface MediaUploadQuotaMapper extends BaseMapperX<MediaUploadQuotaDO> {

    @Insert("""
            INSERT IGNORE INTO infra_media_upload_quota
                (owner_user_id, owner_user_type, quota_date, upload_count, upload_bytes,
                 reserved_storage_bytes, creator, updater, deleted)
            VALUES (#{userId}, #{userType}, #{quotaDate}, 0, 0, 0, '', '', FALSE)
            """)
    int ensureQuotaRow(@Param("userId") Long userId, @Param("userType") Integer userType,
                       @Param("quotaDate") LocalDate quotaDate);

    @Update("""
            UPDATE infra_media_upload_quota q
            SET q.upload_count = q.upload_count + 1,
                q.upload_bytes = q.upload_bytes + #{bytes},
                q.reserved_storage_bytes = q.reserved_storage_bytes + #{bytes}
            WHERE q.owner_user_id = #{userId}
              AND q.owner_user_type = #{userType}
              AND q.quota_date = #{quotaDate}
              AND q.deleted = FALSE
              AND q.upload_count + 1 <= #{maxDailyCount}
              AND q.upload_bytes + #{bytes} <= #{maxDailyBytes}
              AND q.reserved_storage_bytes + #{bytes} +
                  COALESCE((SELECT SUM(f.size) FROM infra_file f
                            WHERE f.owner_user_id = #{userId}
                              AND f.owner_user_type = #{userType}
                              AND f.asset_status IN (0, 10, 20)
                              AND f.deleted = FALSE), 0) <= #{maxTotalBytes}
            """)
    int reserve(@Param("userId") Long userId, @Param("userType") Integer userType,
                @Param("quotaDate") LocalDate quotaDate, @Param("bytes") long bytes,
                @Param("maxDailyCount") int maxDailyCount,
                @Param("maxDailyBytes") long maxDailyBytes,
                @Param("maxTotalBytes") long maxTotalBytes);

    @Update("""
            UPDATE infra_media_upload_quota
            SET reserved_storage_bytes = GREATEST(0, reserved_storage_bytes - #{bytes})
            WHERE owner_user_id = #{userId}
              AND owner_user_type = #{userType}
              AND quota_date = #{quotaDate}
              AND deleted = FALSE
            """)
    int commitStorage(@Param("userId") Long userId, @Param("userType") Integer userType,
                      @Param("quotaDate") LocalDate quotaDate, @Param("bytes") long bytes);

    @Update("""
            UPDATE infra_media_upload_quota
            SET upload_count = GREATEST(0, upload_count - 1),
                upload_bytes = GREATEST(0, upload_bytes - #{bytes}),
                reserved_storage_bytes = GREATEST(0, reserved_storage_bytes - #{bytes})
            WHERE owner_user_id = #{userId}
              AND owner_user_type = #{userType}
              AND quota_date = #{quotaDate}
              AND deleted = FALSE
            """)
    int release(@Param("userId") Long userId, @Param("userType") Integer userType,
                @Param("quotaDate") LocalDate quotaDate, @Param("bytes") long bytes);
}
