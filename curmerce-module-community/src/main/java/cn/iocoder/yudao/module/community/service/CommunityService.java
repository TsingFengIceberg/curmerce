package cn.iocoder.yudao.module.community.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.community.controller.admin.vo.*;
import cn.iocoder.yudao.module.community.controller.app.comment.vo.*;
import cn.iocoder.yudao.module.community.controller.app.interaction.vo.*;
import cn.iocoder.yudao.module.community.controller.app.post.vo.*;
import cn.iocoder.yudao.module.community.controller.app.report.vo.CommunityReportCreateReqVO;

import java.util.List;

public interface CommunityService {
    Long createPost(Long userId, CommunityPostCreateReqVO req);
    void updatePost(Long userId, CommunityPostUpdateReqVO req);
    void submitPost(Long userId, Long id);
    void deletePost(Long userId, Long id);
    CommunityPostRespVO getPost(Long viewerId, Long id);
    PageResult<CommunityPostRespVO> getFeed(Long viewerId, CommunityPostPageReqVO req);
    List<CommunityTopicRespVO> getPopularTopics(Integer limit);
    PageResult<CommunityPostRespVO> getOwnerPosts(Long userId, CommunityPostOwnerPageReqVO req);
    PageResult<CommunityPostRespVO> getFavoritePosts(Long userId, CommunityPostOwnerPageReqVO req);
    PageResult<CommunityPostRespVO> getFollowingPosts(Long userId, CommunityPostOwnerPageReqVO req);
    Long createComment(Long userId, CommunityCommentCreateReqVO req);
    PageResult<CommunityCommentRespVO> getComments(Long viewerId, CommunityCommentPageReqVO req);
    void setReaction(Long userId, CommunityReactionReqVO req);
    void setFollow(Long userId, CommunityFollowReqVO req);
    Long report(Long userId, CommunityReportCreateReqVO req);
    PageResult<CommunityPostRespVO> getAdminPosts(CommunityPostAdminPageReqVO req);
    void setPostStatus(Long adminUserId, CommunityPostStatusReqVO req);
    PageResult<CommunityReportRespVO> getAdminReports(CommunityReportPageReqVO req);
    void reviewReport(Long adminUserId, CommunityReportReviewReqVO req);
}
