package cn.iocoder.yudao.module.community.dal.mysql.topic;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.community.dal.dataobject.topic.CommunityTopicDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommunityTopicMapper extends BaseMapperX<CommunityTopicDO> {
    default CommunityTopicDO selectBySlug(String slug) {
        return selectOne(new LambdaQueryWrapper<CommunityTopicDO>().eq(CommunityTopicDO::getSlug, slug));
    }
}
