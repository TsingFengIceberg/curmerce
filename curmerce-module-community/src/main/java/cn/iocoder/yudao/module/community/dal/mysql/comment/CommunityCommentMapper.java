package cn.iocoder.yudao.module.community.dal.mysql.comment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.community.controller.app.comment.vo.CommunityCommentPageReqVO;
import cn.iocoder.yudao.module.community.dal.dataobject.comment.CommunityCommentDO;
import cn.iocoder.yudao.module.community.enums.CommunityCommentStatusEnum;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommunityCommentMapper extends BaseMapperX<CommunityCommentDO> {
    default PageResult<CommunityCommentDO> selectVisiblePage(CommunityCommentPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<CommunityCommentDO>()
                .eq(CommunityCommentDO::getPostId, req.getPostId())
                .eq(CommunityCommentDO::getStatus, CommunityCommentStatusEnum.VISIBLE.getStatus())
                .orderByAsc(CommunityCommentDO::getId));
    }
}
