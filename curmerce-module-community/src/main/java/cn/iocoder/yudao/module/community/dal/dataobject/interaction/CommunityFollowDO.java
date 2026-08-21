package cn.iocoder.yudao.module.community.dal.dataobject.interaction;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("community_follow")
@KeySequence("community_follow_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityFollowDO extends BaseDO {
    @TableId private Long id;
    private Long followerUserId;
    private Long followedUserId;
}
