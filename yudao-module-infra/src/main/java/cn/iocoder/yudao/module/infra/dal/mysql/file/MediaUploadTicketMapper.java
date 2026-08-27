package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaUploadTicketDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.infra.enums.file.MediaUploadTicketStatus.ISSUED;
import static cn.iocoder.yudao.module.infra.enums.file.MediaUploadTicketStatus.PROCESSING;

@Mapper
public interface MediaUploadTicketMapper extends BaseMapperX<MediaUploadTicketDO> {

    default MediaUploadTicketDO selectByTicketKey(String ticketKey) {
        return selectOne(MediaUploadTicketDO::getTicketKey, ticketKey);
    }

    default List<MediaUploadTicketDO> selectExpired(LocalDateTime now, LocalDateTime staleBefore, int limit) {
        return selectList(new LambdaQueryWrapperX<MediaUploadTicketDO>()
                .and(wrapper -> wrapper
                        .and(issued -> issued.eq(MediaUploadTicketDO::getStatus, ISSUED.getStatus())
                                .le(MediaUploadTicketDO::getExpiresAt, now))
                        .or(processing -> processing.eq(MediaUploadTicketDO::getStatus, PROCESSING.getStatus())
                                .le(MediaUploadTicketDO::getUpdateTime, staleBefore)))
                .orderByAsc(MediaUploadTicketDO::getExpiresAt)
                .last("LIMIT " + limit));
    }

    @Update("UPDATE infra_media_upload_ticket SET status = " + 5 + " WHERE id = #{id} AND status = " + 0 + " AND deleted = FALSE")
    int claim(@Param("id") Long id);

    @Update("""
            UPDATE infra_media_upload_ticket
            SET status = 5, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted = FALSE
              AND ((status = 0 AND expires_at <= #{now})
                   OR (status = 5 AND update_time <= #{staleBefore}))
            """)
    int claimExpired(@Param("id") Long id, @Param("now") LocalDateTime now,
                     @Param("staleBefore") LocalDateTime staleBefore);

    @Update("UPDATE infra_media_upload_ticket SET status = " + 0 + " WHERE id = #{id} AND status = " + 5 + " AND deleted = FALSE")
    int releaseClaim(@Param("id") Long id);
}
