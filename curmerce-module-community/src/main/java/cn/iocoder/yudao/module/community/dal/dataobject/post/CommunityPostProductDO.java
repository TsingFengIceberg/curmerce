package cn.iocoder.yudao.module.community.dal.dataobject.post;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("community_post_product")
@KeySequence("community_post_product_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityPostProductDO extends BaseDO {
    @TableId private Long id;
    private Long postId;
    private Long productId;
    private Integer sort;
}
