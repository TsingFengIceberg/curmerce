package cn.iocoder.yudao.module.community.dal.mysql.outbox;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunityMediaOutboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CommunityMediaOutboxMapper extends BaseMapperX<CommunityMediaOutboxDO> {

    @Insert("""
            INSERT INTO community_media_outbox
              (business_type, business_id, field_name, payload, version, status, attempts,
               next_retry_time, creator, create_time, updater, update_time, deleted)
            VALUES
              (#{businessType}, #{businessId}, #{fieldName}, #{payload}, 1, 10, 0,
               CURRENT_TIMESTAMP, '', CURRENT_TIMESTAMP, '', CURRENT_TIMESTAMP, b'0')
            ON DUPLICATE KEY UPDATE
              version = IF(payload = VALUES(payload), version, version + 1),
              status = IF(payload = VALUES(payload), status,
                IF(status = 20 AND lease_until > CURRENT_TIMESTAMP, 20, 10)),
              attempts = IF(payload = VALUES(payload), attempts,
                IF(status = 20 AND lease_until > CURRENT_TIMESTAMP, attempts, 0)),
              next_retry_time = IF(payload = VALUES(payload), next_retry_time,
                IF(status = 20 AND lease_until > CURRENT_TIMESTAMP, next_retry_time, CURRENT_TIMESTAMP)),
              processing_token = IF(payload = VALUES(payload), processing_token,
                IF(status = 20 AND lease_until > CURRENT_TIMESTAMP, processing_token, NULL)),
              lease_until = IF(payload = VALUES(payload), lease_until,
                IF(status = 20 AND lease_until > CURRENT_TIMESTAMP, lease_until, NULL)),
              last_error = IF(payload = VALUES(payload), last_error, NULL),
              processed_time = IF(payload = VALUES(payload), processed_time, NULL),
              payload = VALUES(payload),
              update_time = CURRENT_TIMESTAMP,
              deleted = b'0'
            """)
    int upsertDesiredState(@Param("businessType") String businessType,
                           @Param("businessId") String businessId,
                           @Param("fieldName") String fieldName,
                           @Param("payload") String payload);

    @Select("""
            SELECT * FROM community_media_outbox
            WHERE deleted = b'0' AND (
              (status = 10 AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP))
              OR (status = 20 AND lease_until <= CURRENT_TIMESTAMP)
            )
            ORDER BY id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    CommunityMediaOutboxDO selectClaimableForUpdate();

    @Update("""
            UPDATE community_media_outbox
            SET status = 20, processing_token = #{token}, lease_until = #{leaseUntil},
                next_retry_time = NULL, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{version} AND deleted = b'0'
              AND ((status = 10 AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP))
                OR (status = 20 AND lease_until <= CURRENT_TIMESTAMP))
            """)
    int markProcessing(@Param("id") Long id, @Param("version") Long version,
                       @Param("token") String token, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE community_media_outbox
            SET status = 30, processing_token = NULL, lease_until = NULL, next_retry_time = NULL,
                last_error = NULL, processed_time = #{processedTime}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{version} AND processing_token = #{token}
              AND status = 20 AND deleted = b'0'
            """)
    int markSucceeded(@Param("id") Long id, @Param("version") Long version,
                      @Param("token") String token, @Param("processedTime") LocalDateTime processedTime);

    @Update("""
            UPDATE community_media_outbox
            SET status = 10, attempts = #{attempts}, next_retry_time = #{nextRetryTime},
                processing_token = NULL, lease_until = NULL, last_error = #{lastError},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{version} AND processing_token = #{token}
              AND status = 20 AND deleted = b'0'
            """)
    int markRetry(@Param("id") Long id, @Param("version") Long version, @Param("token") String token,
                  @Param("attempts") Integer attempts, @Param("nextRetryTime") LocalDateTime nextRetryTime,
                  @Param("lastError") String lastError);

    @Update("""
            UPDATE community_media_outbox
            SET status = 10, attempts = 0, next_retry_time = CURRENT_TIMESTAMP,
                processing_token = NULL, lease_until = NULL, last_error = NULL,
                processed_time = NULL, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version <> #{processedVersion} AND processing_token = #{token}
              AND status = 20 AND deleted = b'0'
            """)
    int releaseNewerVersion(@Param("id") Long id, @Param("processedVersion") Long processedVersion,
                            @Param("token") String token);

    @Select("SELECT COUNT(*) FROM community_media_outbox WHERE deleted = b'0' AND status <> 30")
    long countUnfinished();
}
