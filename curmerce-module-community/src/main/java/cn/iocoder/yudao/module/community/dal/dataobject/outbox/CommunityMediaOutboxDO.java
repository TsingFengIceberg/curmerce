package cn.iocoder.yudao.module.community.dal.dataobject.outbox;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("community_media_outbox")
@KeySequence("community_media_outbox_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityMediaOutboxDO extends BaseDO {
    @TableId private Long id;
    private String businessType;
    private String businessId;
    private String fieldName;
    private String payload;
    private Long version;
    private Integer status;
    private Integer attempts;
    private LocalDateTime nextRetryTime;
    private String processingToken;
    private LocalDateTime leaseUntil;
    private String lastError;
    private LocalDateTime processedTime;
}
