package cn.iocoder.yudao.module.community.dal.mysql.interaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.community.dal.dataobject.interaction.CommunityReactionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommunityReactionMapper extends BaseMapperX<CommunityReactionDO> {
    default CommunityReactionDO selectOne(Long postId, Long userId, Integer type) {
        return selectOne(new LambdaQueryWrapper<CommunityReactionDO>().eq(CommunityReactionDO::getPostId, postId)
                .eq(CommunityReactionDO::getUserId, userId).eq(CommunityReactionDO::getType, type));
    }
    default int deleteOne(Long postId, Long userId, Integer type) {
        return delete(new LambdaQueryWrapper<CommunityReactionDO>().eq(CommunityReactionDO::getPostId, postId)
                .eq(CommunityReactionDO::getUserId, userId).eq(CommunityReactionDO::getType, type));
    }
}
