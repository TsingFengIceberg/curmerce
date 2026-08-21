package cn.iocoder.yudao.module.community.dal.mysql.post;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.community.controller.admin.vo.CommunityPostAdminPageReqVO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostPageReqVO;
import cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostDO;
import cn.iocoder.yudao.module.community.enums.CommunityPostStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import cn.hutool.core.util.StrUtil;

@Mapper
public interface CommunityPostMapper extends BaseMapperX<CommunityPostDO> {
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
