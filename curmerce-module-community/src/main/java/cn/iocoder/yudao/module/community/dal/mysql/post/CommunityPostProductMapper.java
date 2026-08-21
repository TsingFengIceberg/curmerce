package cn.iocoder.yudao.module.community.dal.mysql.post;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostProductDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommunityPostProductMapper extends BaseMapperX<CommunityPostProductDO> {
    default List<CommunityPostProductDO> selectByPostId(Long postId) {
        return selectList(new LambdaQueryWrapper<CommunityPostProductDO>().eq(CommunityPostProductDO::getPostId, postId)
                .orderByAsc(CommunityPostProductDO::getSort).orderByAsc(CommunityPostProductDO::getId));
    }
    default void deleteByPostId(Long postId) {
        delete(new LambdaQueryWrapper<CommunityPostProductDO>().eq(CommunityPostProductDO::getPostId, postId));
    }
}
