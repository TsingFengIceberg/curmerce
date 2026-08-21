package cn.iocoder.yudao.module.community.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.community.controller.app.interaction.vo.CommunityFollowReqVO;
import cn.iocoder.yudao.module.community.controller.app.interaction.vo.CommunityReactionReqVO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostCreateReqVO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostUpdateReqVO;
import cn.iocoder.yudao.module.community.controller.app.report.vo.CommunityReportCreateReqVO;
import cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostDO;
import cn.iocoder.yudao.module.community.dal.mysql.comment.CommunityCommentMapper;
import cn.iocoder.yudao.module.community.dal.mysql.interaction.CommunityFollowMapper;
import cn.iocoder.yudao.module.community.dal.mysql.interaction.CommunityReactionMapper;
import cn.iocoder.yudao.module.community.dal.mysql.post.CommunityPostMapper;
import cn.iocoder.yudao.module.community.dal.mysql.post.CommunityPostProductMapper;
import cn.iocoder.yudao.module.community.dal.mysql.report.CommunityReportMapper;
import cn.iocoder.yudao.module.community.dal.mysql.topic.CommunityPostTopicMapper;
import cn.iocoder.yudao.module.community.dal.mysql.topic.CommunityTopicMapper;
import cn.iocoder.yudao.module.community.enums.CommunityPostStatusEnum;
import cn.iocoder.yudao.module.commerce.service.catalog.PublicCatalogService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.community.enums.ErrorCodeConstants.FOLLOW_SELF_INVALID;
import static cn.iocoder.yudao.module.community.enums.ErrorCodeConstants.POST_STATE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceImplTest {
    @Mock private CommunityPostMapper postMapper;
    @Mock private CommunityPostProductMapper postProductMapper;
    @Mock private CommunityTopicMapper topicMapper;
    @Mock private CommunityPostTopicMapper postTopicMapper;
    @Mock private CommunityCommentMapper commentMapper;
    @Mock private CommunityReactionMapper reactionMapper;
    @Mock private CommunityFollowMapper followMapper;
    @Mock private CommunityReportMapper reportMapper;
    @Mock private MemberUserApi memberUserApi;
    @Mock private PublicCatalogService catalogService;
    @InjectMocks private CommunityServiceImpl service;

    @Test
    void createPost_persistsDraftAndRelations() {
        doAnswer(invocation -> { ((CommunityPostDO) invocation.getArgument(0)).setId(10L); return 1; }).when(postMapper).insert(any(CommunityPostDO.class));
        Long id = service.createPost(7L, new CommunityPostCreateReqVO().setTitle(" Title ").setContent(" Body "));
        assertEquals(10L, id);
        verify(postMapper).insert(argThat((CommunityPostDO post) -> post.getId().equals(10L)
                && post.getAuthorUserId().equals(7L) && post.getStatus().equals(CommunityPostStatusEnum.DRAFT.getStatus())
                && post.getTitle().equals("Title")));
        verify(postProductMapper).deleteByPostId(10L);
        verify(postTopicMapper).deleteByPostId(10L);
    }

    @Test
    void updatePost_rejectsOtherAuthorsAndPublishedPosts() {
        when(postMapper.selectByIdForUpdate(10L)).thenReturn(new CommunityPostDO().setId(10L).setAuthorUserId(8L).setStatus(0));
        CommunityPostUpdateReqVO other = updateRequest(10L);
        assertThrows(ServiceException.class, () -> service.updatePost(7L, other));
        when(postMapper.selectByIdForUpdate(11L)).thenReturn(new CommunityPostDO().setId(11L).setAuthorUserId(7L).setStatus(1));
        ServiceException error = assertThrows(ServiceException.class, () -> service.updatePost(7L, updateRequest(11L)));
        assertEquals(POST_STATE_INVALID.getCode(), error.getCode());
        verify(postMapper, never()).updateOwnerFields(any(CommunityPostDO.class), anyLong());
    }

    @Test
    void reaction_isIdempotentForRepeatedActivation() {
        when(postMapper.selectById(10L)).thenReturn(new CommunityPostDO().setId(10L).setStatus(1));
        when(reactionMapper.selectOne(10L, 7L, 1)).thenReturn(new cn.iocoder.yudao.module.community.dal.dataobject.interaction.CommunityReactionDO());
        service.setReaction(7L, new CommunityReactionReqVO().setPostId(10L).setType(1).setActive(true));
        verify(reactionMapper, never()).insert(any(cn.iocoder.yudao.module.community.dal.dataobject.interaction.CommunityReactionDO.class));
        verify(postMapper, never()).incrementLike(anyLong(), anyInt());
    }

    @Test
    void follow_rejectsSelfAndDoesNotWrite() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.setFollow(7L, new CommunityFollowReqVO().setUserId(7L).setActive(true)));
        assertEquals(FOLLOW_SELF_INVALID.getCode(), error.getCode());
        verify(followMapper, never()).insert(any(cn.iocoder.yudao.module.community.dal.dataobject.interaction.CommunityFollowDO.class));
    }

    @Test
    void report_repeatedPendingSubmissionIsIdempotent() {
        when(postMapper.selectById(10L)).thenReturn(new CommunityPostDO().setId(10L).setStatus(1));
        when(reportMapper.selectPending(10L, 7L)).thenReturn(new cn.iocoder.yudao.module.community.dal.dataobject.report.CommunityReportDO().setId(22L));
        Long reportId = service.report(7L, new CommunityReportCreateReqVO().setPostId(10L).setReason("spam"));
        assertEquals(22L, reportId);
        verify(reportMapper, never()).insert(any(cn.iocoder.yudao.module.community.dal.dataobject.report.CommunityReportDO.class));
    }

    private CommunityPostUpdateReqVO updateRequest(Long id) {
        CommunityPostUpdateReqVO request = new CommunityPostUpdateReqVO();
        request.setId(id); request.setTitle("Title"); request.setContent("Body");
        return request;
    }
}
