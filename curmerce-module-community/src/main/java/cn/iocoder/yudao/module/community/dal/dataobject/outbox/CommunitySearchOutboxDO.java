package cn.iocoder.yudao.module.community.dal.dataobject.outbox;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("community_search_outbox")
@KeySequence("community_search_outbox_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunitySearchOutboxDO extends BaseDO {
    @TableId private Long id;
    private String eventType;
    private String eventKey;
    private String aggregateType;
    private Long aggregateId;
    private String payload;
    private Integer status;
    private Integer attempts;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime publishedTime;
}
