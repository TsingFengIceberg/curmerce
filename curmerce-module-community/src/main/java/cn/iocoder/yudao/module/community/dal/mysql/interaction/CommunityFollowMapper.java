package cn.iocoder.yudao.module.community.dal.mysql.interaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.community.dal.dataobject.interaction.CommunityFollowDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommunityFollowMapper extends BaseMapperX<CommunityFollowDO> {
    default CommunityFollowDO selectOne(Long follower, Long followed) {
        return selectOne(new LambdaQueryWrapper<CommunityFollowDO>().eq(CommunityFollowDO::getFollowerUserId, follower)
                .eq(CommunityFollowDO::getFollowedUserId, followed));
    }
    default int deleteOne(Long follower, Long followed) {
        return delete(new LambdaQueryWrapper<CommunityFollowDO>().eq(CommunityFollowDO::getFollowerUserId, follower)
                .eq(CommunityFollowDO::getFollowedUserId, followed));
    }
}
