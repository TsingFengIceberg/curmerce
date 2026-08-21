package cn.iocoder.yudao.module.community.dal.dataobject.comment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("community_comment")
@KeySequence("community_comment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityCommentDO extends BaseDO {
    @TableId private Long id;
    private Long postId;
    private Long parentId;
    private Long authorUserId;
    private String content;
    private Integer status;
}
