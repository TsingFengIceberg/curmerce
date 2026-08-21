package cn.iocoder.yudao.module.community.dal.mysql.topic;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.community.dal.dataobject.topic.CommunityPostTopicDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommunityPostTopicMapper extends BaseMapperX<CommunityPostTopicDO> {
    default List<CommunityPostTopicDO> selectByPostId(Long postId) {
        return selectList(new LambdaQueryWrapper<CommunityPostTopicDO>().eq(CommunityPostTopicDO::getPostId, postId));
    }
    default void deleteByPostId(Long postId) {
        delete(new LambdaQueryWrapper<CommunityPostTopicDO>().eq(CommunityPostTopicDO::getPostId, postId));
    }
}
