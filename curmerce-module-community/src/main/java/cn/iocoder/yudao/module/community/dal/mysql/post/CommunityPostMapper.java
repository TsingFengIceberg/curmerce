package cn.iocoder.yudao.module.community.dal.mysql.post;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.community.controller.admin.vo.CommunityPostAdminPageReqVO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostPageReqVO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostOwnerPageReqVO;
import cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostDO;
import cn.iocoder.yudao.module.community.enums.CommunityPostStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import cn.hutool.core.util.StrUtil;

@Mapper
public interface CommunityPostMapper extends BaseMapperX<CommunityPostDO> {
    default java.util.List<CommunityPostDO> selectMediaBatchAfterId(long afterId, int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        return selectList(new LambdaQueryWrapper<CommunityPostDO>()
                .gt(CommunityPostDO::getId, afterId).orderByAsc(CommunityPostDO::getId)
                .last("LIMIT " + safeBatchSize));
    }
    default CommunityPostDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommunityPostDO>().eq(CommunityPostDO::getId, id));
    }
    default PageResult<CommunityPostDO> selectPublishedPage(CommunityPostPageReqVO req) {
        LambdaQueryWrapperX<CommunityPostDO> wrapper = new LambdaQueryWrapperX<CommunityPostDO>()
                .eq(CommunityPostDO::getStatus, CommunityPostStatusEnum.PUBLISHED.getStatus());
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.and(w -> w.like(CommunityPostDO::getTitle, req.getKeyword())
                    .or().like(CommunityPostDO::getContent, req.getKeyword()));
        }
        if (StrUtil.isNotBlank(req.getTopicSlug())) {
            wrapper.apply("EXISTS (SELECT 1 FROM community_post_topic cpt JOIN community_topic ct ON ct.id = cpt.topic_id " +
                    "WHERE cpt.post_id = community_post.id AND cpt.deleted = 0 AND ct.deleted = 0 AND ct.status = 0 AND ct.slug = {0})",
                    req.getTopicSlug().trim());
        }
        if (req.getProductId() != null) {
            wrapper.apply("EXISTS (SELECT 1 FROM community_post_product cpp WHERE cpp.post_id = community_post.id " +
                    "AND cpp.deleted = 0 AND cpp.product_id = {0})", req.getProductId());
        }
        return selectPage(req, wrapper.orderByDesc(CommunityPostDO::getId));
    }
    default PageResult<CommunityPostDO> selectOwnerPage(Long userId, CommunityPostPageReqVO req) {
        LambdaQueryWrapperX<CommunityPostDO> wrapper = new LambdaQueryWrapperX<CommunityPostDO>()
                .eq(CommunityPostDO::getAuthorUserId, userId);
        wrapper.in(CommunityPostDO::getStatus, CommunityPostStatusEnum.DRAFT.getStatus(), CommunityPostStatusEnum.HIDDEN.getStatus(), CommunityPostStatusEnum.PUBLISHED.getStatus());
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.and(w -> w.like(CommunityPostDO::getTitle, req.getKeyword())
                    .or().like(CommunityPostDO::getContent, req.getKeyword()));
        }
        return selectPage(req, wrapper.orderByDesc(CommunityPostDO::getId));
    }
    default PageResult<CommunityPostDO> selectFavoritePage(Long userId, CommunityPostOwnerPageReqVO req) {
        LambdaQueryWrapperX<CommunityPostDO> wrapper = new LambdaQueryWrapperX<CommunityPostDO>()
                .eq(CommunityPostDO::getStatus, CommunityPostStatusEnum.PUBLISHED.getStatus());
        wrapper.apply("EXISTS (SELECT 1 FROM community_post_reaction cpr WHERE cpr.post_id = community_post.id " +
                "AND cpr.user_id = {0} AND cpr.type = 2 AND cpr.deleted = 0)", userId);
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.and(w -> w.like(CommunityPostDO::getTitle, req.getKeyword())
                    .or().like(CommunityPostDO::getContent, req.getKeyword()));
        }
        return selectPage(req, wrapper.orderByDesc(CommunityPostDO::getId));
    }
    default PageResult<CommunityPostDO> selectFollowingPage(Long userId, CommunityPostOwnerPageReqVO req) {
        LambdaQueryWrapperX<CommunityPostDO> wrapper = new LambdaQueryWrapperX<CommunityPostDO>()
                .eq(CommunityPostDO::getStatus, CommunityPostStatusEnum.PUBLISHED.getStatus());
        wrapper.apply("EXISTS (SELECT 1 FROM community_follow cf WHERE cf.followed_user_id = community_post.author_user_id " +
                "AND cf.follower_user_id = {0} AND cf.deleted = 0)", userId);
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.and(w -> w.like(CommunityPostDO::getTitle, req.getKeyword())
                    .or().like(CommunityPostDO::getContent, req.getKeyword()));
        }
        return selectPage(req, wrapper.orderByDesc(CommunityPostDO::getId));
    }
    default PageResult<CommunityPostDO> selectAdminPage(CommunityPostAdminPageReqVO req) {
        LambdaQueryWrapperX<CommunityPostDO> wrapper = new LambdaQueryWrapperX<CommunityPostDO>()
                .eqIfPresent(CommunityPostDO::getStatus, req.getStatus());
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.and(w -> w.like(CommunityPostDO::getTitle, req.getKeyword())
                    .or().like(CommunityPostDO::getContent, req.getKeyword()));
        }
        return selectPage(req, wrapper.orderByDesc(CommunityPostDO::getId));
    }
    default int updateOwnerFields(CommunityPostDO update, Long userId) {
        return update(update, new LambdaUpdateWrapper<CommunityPostDO>().eq(CommunityPostDO::getId, update.getId())
                .eq(CommunityPostDO::getAuthorUserId, userId)
                .in(CommunityPostDO::getStatus, CommunityPostStatusEnum.DRAFT.getStatus(), CommunityPostStatusEnum.HIDDEN.getStatus()));
    }
    default int updateStatus(Long id, Integer expected, Integer target) {
        return update(new CommunityPostDO().setStatus(target), new LambdaUpdateWrapper<CommunityPostDO>()
                .eq(CommunityPostDO::getId, id).eq(CommunityPostDO::getStatus, expected));
    }
    default int incrementLike(Long id, int delta) {
        return update(null, new LambdaUpdateWrapper<CommunityPostDO>().eq(CommunityPostDO::getId, id)
                .setSql("like_count = GREATEST(0, like_count + " + delta + ")"));
    }
    default int incrementFavorite(Long id, int delta) {
        return update(null, new LambdaUpdateWrapper<CommunityPostDO>().eq(CommunityPostDO::getId, id)
                .setSql("favorite_count = GREATEST(0, favorite_count + " + delta + ")"));
    }
    default int incrementComment(Long id, int delta) {
        return update(null, new LambdaUpdateWrapper<CommunityPostDO>().eq(CommunityPostDO::getId, id)
                .setSql("comment_count = GREATEST(0, comment_count + " + delta + ")"));
    }
}
