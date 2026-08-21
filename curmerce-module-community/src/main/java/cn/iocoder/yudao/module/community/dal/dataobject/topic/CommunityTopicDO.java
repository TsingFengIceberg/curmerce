package cn.iocoder.yudao.module.community.dal.dataobject.topic;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("community_topic")
@KeySequence("community_topic_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityTopicDO extends BaseDO {
    @TableId private Long id;
    private String name;
    private String slug;
    private Integer status;
}
