package cn.iocoder.yudao.module.community.dal.mysql.topic;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.community.dal.dataobject.topic.CommunityTopicDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommunityTopicMapper extends BaseMapperX<CommunityTopicDO> {
    default CommunityTopicDO selectBySlug(String slug) {
        return selectOne(new LambdaQueryWrapper<CommunityTopicDO>().eq(CommunityTopicDO::getSlug, slug));
    }

    @Select("""
            SELECT ct.id, ct.name, ct.slug, COUNT(DISTINCT cpt.post_id) AS post_count
            FROM community_topic ct
            JOIN community_post_topic cpt ON cpt.topic_id = ct.id AND cpt.deleted = 0
            JOIN community_post cp ON cp.id = cpt.post_id AND cp.deleted = 0 AND cp.status = 1
            WHERE ct.deleted = 0 AND ct.status = 0
            GROUP BY ct.id, ct.name, ct.slug
            ORDER BY post_count DESC, ct.id DESC
            LIMIT #{limit}
            """)
    List<CommunityTopicDO> selectPopularTopics(int limit);
}
