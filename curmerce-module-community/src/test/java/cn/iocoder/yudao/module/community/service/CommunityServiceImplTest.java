package cn.iocoder.yudao.module.community.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.community.controller.app.interaction.vo.CommunityFollowReqVO;
import cn.iocoder.yudao.module.community.controller.app.interaction.vo.CommunityReactionReqVO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostCreateReqVO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityProductSummaryRespVO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostUpdateReqVO;
import cn.iocoder.yudao.module.community.controller.app.report.vo.CommunityReportCreateReqVO;
import cn.iocoder.yudao.module.community.controller.admin.vo.CommunityReportPageReqVO;
import cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostDO;
import cn.iocoder.yudao.module.community.dal.dataobject.report.CommunityReportDO;
import cn.iocoder.yudao.module.community.dal.dataobject.topic.CommunityTopicDO;
import cn.iocoder.yudao.module.community.dal.mysql.comment.CommunityCommentMapper;
import cn.iocoder.yudao.module.community.dal.mysql.interaction.CommunityFollowMapper;
import cn.iocoder.yudao.module.community.dal.mysql.interaction.CommunityReactionMapper;
import cn.iocoder.yudao.module.community.dal.mysql.post.CommunityPostMapper;
import cn.iocoder.yudao.module.community.dal.mysql.post.CommunityPostProductMapper;
import cn.iocoder.yudao.module.community.dal.mysql.report.CommunityReportMapper;
import cn.iocoder.yudao.module.community.dal.mysql.topic.CommunityPostTopicMapper;
import cn.iocoder.yudao.module.community.dal.mysql.topic.CommunityTopicMapper;
import cn.iocoder.yudao.module.community.enums.CommunityPostStatusEnum;
import cn.iocoder.yudao.module.community.service.integration.CommunityMediaClient;
import cn.iocoder.yudao.module.community.service.integration.CommunityMemberClient;
import cn.iocoder.yudao.module.community.service.integration.CommunityMemberProfile;
import cn.iocoder.yudao.module.community.service.integration.CommunityProductClient;
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

import java.util.Collections;
import java.util.List;

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
    @Mock private CommunityMemberClient memberClient;
    @Mock private CommunityProductClient productClient;
    @Mock private CommunityMediaClient mediaClient;
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
    void createPost_validatesAndPersistsVisibleProductAssociations() {
        doAnswer(invocation -> { ((CommunityPostDO) invocation.getArgument(0)).setId(11L); return 1; }).when(postMapper).insert(any(CommunityPostDO.class));
        CommunityProductSummaryRespVO product = new CommunityProductSummaryRespVO();
        product.setId(201L);
        when(productClient.getVisibleSummary(201L)).thenReturn(product);

        service.createPost(7L, new CommunityPostCreateReqVO().setTitle("Tea review").setContent("Body")
                .setProductIds(java.util.List.of(201L)));

        verify(productClient).getVisibleSummary(201L);
        verify(postProductMapper).insert(argThat((cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostProductDO relation) ->
                relation.getPostId().equals(11L) && relation.getProductId().equals(201L) && relation.getSort().equals(0)));
    }

    @Test
    void createPost_rejectsHiddenOrMissingProductAssociation() {
        when(productClient.getVisibleSummary(201L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.createPost(7L,
                new CommunityPostCreateReqVO().setTitle("Tea review").setContent("Body")
                        .setProductIds(java.util.List.of(201L))));

        verify(postMapper, never()).insert(any(CommunityPostDO.class));
        verify(postProductMapper, never()).insert(any(cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostProductDO.class));
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

    @Test
    void getAdminReports_enrichesPostAndMemberContext() {
        CommunityReportDO report = new CommunityReportDO().setId(22L).setPostId(10L).setReporterUserId(8L)
                .setReason("spam").setStatus(0);
        when(reportMapper.selectAdminPage(any())).thenReturn(new PageResult<>(List.of(report), 1L));
        when(postMapper.selectById(10L)).thenReturn(new CommunityPostDO().setId(10L).setAuthorUserId(7L)
                .setTitle("Post title").setContent("Post body").setMediaUrls(List.of("https://example.com/image.jpg")));
        when(memberClient.getUser(7L)).thenReturn(new CommunityMemberProfile().setId(7L).setNickname("Author"));
        when(memberClient.getUser(8L)).thenReturn(new CommunityMemberProfile().setId(8L).setNickname("Reporter"));

        var result = service.getAdminReports(new CommunityReportPageReqVO());

        assertEquals(1L, result.getTotal());
        assertEquals("Post title", result.getList().getFirst().getPostTitle());
        assertEquals("Post body", result.getList().getFirst().getPostContent());
        assertEquals(List.of("https://example.com/image.jpg"), result.getList().getFirst().getPostMediaUrls());
        assertEquals("Author", result.getList().getFirst().getPostAuthorNickname());
        assertEquals("Reporter", result.getList().getFirst().getReporterNickname());
    }

    @Test
    void getPopularTopics_returnsUsageCountsAndCapsRequestedLimit() {
        when(topicMapper.selectPopularTopics(50)).thenReturn(List.of(
                new CommunityTopicDO().setId(5L).setName("手冲咖啡").setSlug("coffee").setPostCount(12L)));

        var result = service.getPopularTopics(500);

        assertEquals(1, result.size());
        assertEquals("手冲咖啡", result.getFirst().getName());
        assertEquals(12L, result.getFirst().getPostCount());
        verify(topicMapper).selectPopularTopics(50);
    }

    @Test
    void getPost_enrichesAuthorAvatar() {
        when(postMapper.selectById(10L)).thenReturn(new CommunityPostDO().setId(10L).setAuthorUserId(7L)
                .setTitle("Post").setContent("Body").setStatus(CommunityPostStatusEnum.PUBLISHED.getStatus()));
        when(memberClient.getUser(7L)).thenReturn(new CommunityMemberProfile().setId(7L)
                .setNickname("Author").setAvatar("/avatar.png"));
        when(postTopicMapper.selectByPostId(10L)).thenReturn(List.of());
        when(postProductMapper.selectByPostId(10L)).thenReturn(List.of());

        var result = service.getPost(null, 10L);

        assertEquals("Author", result.getAuthorNickname());
        assertEquals("/avatar.png", result.getAuthorAvatar());
    }

    @Test
    void readOnlyOwnerQueriesUseNonLockingUserValidation() {
        when(postMapper.selectOwnerPage(eq(7L), any())).thenReturn(emptyPostPage());
        when(postMapper.selectFavoritePage(eq(7L), any())).thenReturn(emptyPostPage());
        when(postMapper.selectFollowingPage(eq(7L), any())).thenReturn(emptyPostPage());

        service.getOwnerPosts(7L, new cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostOwnerPageReqVO());
        service.getFavoritePosts(7L, new cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostOwnerPageReqVO());
        service.getFollowingPosts(7L, new cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostOwnerPageReqVO());

        verify(memberClient, times(3)).validateActiveUser(7L);
        verify(memberClient, never()).validateActiveUserForUpdate(anyLong());
    }

    private PageResult<CommunityPostDO> emptyPostPage() {
        return new PageResult<>(Collections.emptyList(), 0L);
    }

    private CommunityPostUpdateReqVO updateRequest(Long id) {
        CommunityPostUpdateReqVO request = new CommunityPostUpdateReqVO();
        request.setId(id); request.setTitle("Title"); request.setContent("Body");
        return request;
    }
}
