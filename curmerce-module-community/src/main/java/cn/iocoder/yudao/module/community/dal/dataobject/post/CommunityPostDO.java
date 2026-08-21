package cn.iocoder.yudao.module.community.dal.dataobject.post;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@TableName(value = "community_post", autoResultMap = true)
@KeySequence("community_post_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityPostDO extends BaseDO {
    @TableId private Long id;
    private Long authorUserId;
    private String title;
    private String content;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> mediaUrls;
    private Integer status;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
}
