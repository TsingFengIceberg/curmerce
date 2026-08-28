package cn.iocoder.yudao.module.community.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.community.controller.admin.vo.*;
import cn.iocoder.yudao.module.community.controller.app.comment.vo.*;
import cn.iocoder.yudao.module.community.controller.app.interaction.vo.*;
import cn.iocoder.yudao.module.community.controller.app.post.vo.*;
import cn.iocoder.yudao.module.community.controller.app.report.vo.CommunityReportCreateReqVO;
import cn.iocoder.yudao.module.community.dal.dataobject.comment.CommunityCommentDO;
import cn.iocoder.yudao.module.community.dal.dataobject.interaction.CommunityFollowDO;
import cn.iocoder.yudao.module.community.dal.dataobject.interaction.CommunityReactionDO;
import cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostDO;
import cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostProductDO;
import cn.iocoder.yudao.module.community.dal.dataobject.report.CommunityReportDO;
import cn.iocoder.yudao.module.community.dal.dataobject.topic.CommunityPostTopicDO;
import cn.iocoder.yudao.module.community.dal.dataobject.topic.CommunityTopicDO;
import cn.iocoder.yudao.module.community.dal.mysql.comment.CommunityCommentMapper;
import cn.iocoder.yudao.module.community.dal.mysql.interaction.CommunityFollowMapper;
import cn.iocoder.yudao.module.community.dal.mysql.interaction.CommunityReactionMapper;
import cn.iocoder.yudao.module.community.dal.mysql.post.CommunityPostMapper;
import cn.iocoder.yudao.module.community.dal.mysql.post.CommunityPostProductMapper;
import cn.iocoder.yudao.module.community.dal.mysql.report.CommunityReportMapper;
import cn.iocoder.yudao.module.community.dal.mysql.topic.CommunityPostTopicMapper;
import cn.iocoder.yudao.module.community.dal.mysql.topic.CommunityTopicMapper;
import cn.iocoder.yudao.module.community.enums.*;
import cn.iocoder.yudao.module.community.service.integration.CommunityMemberClient;
import cn.iocoder.yudao.module.community.service.integration.CommunityMemberProfile;
import cn.iocoder.yudao.module.community.service.integration.CommunityProductClient;
import cn.iocoder.yudao.module.community.service.outbox.CommunityMediaOutboxService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.community.enums.ErrorCodeConstants.*;

@Service
public class CommunityServiceImpl implements CommunityService {
    @Resource(name = "communityPostMapper") private CommunityPostMapper postMapper;
    @Resource(name = "communityPostProductMapper") private CommunityPostProductMapper postProductMapper;
    @Resource(name = "communityTopicMapper") private CommunityTopicMapper topicMapper;
    @Resource(name = "communityPostTopicMapper") private CommunityPostTopicMapper postTopicMapper;
    @Resource(name = "communityCommentMapper") private CommunityCommentMapper commentMapper;
    @Resource(name = "communityReactionMapper") private CommunityReactionMapper reactionMapper;
    @Resource(name = "communityFollowMapper") private CommunityFollowMapper followMapper;
    @Resource(name = "communityReportMapper") private CommunityReportMapper reportMapper;
    @Resource private CommunityMemberClient memberClient;
    @Resource private CommunityProductClient productClient;
    @Resource private CommunityMediaOutboxService mediaOutboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(Long userId, CommunityPostCreateReqVO req) {
        memberClient.validateActiveUserForUpdate(userId);
        NormalizedContent content = normalize(req.getTitle(), req.getContent(), req.getMediaUrls());
        List<Long> products = validateProducts(req.getProductIds());
        CommunityPostDO post = new CommunityPostDO().setAuthorUserId(userId).setTitle(content.title())
                .setContent(content.content()).setMediaUrls(content.mediaUrls())
                .setStatus(CommunityPostStatusEnum.DRAFT.getStatus()).setLikeCount(0).setFavoriteCount(0).setCommentCount(0);
        postMapper.insert(post);
        mediaOutboxService.recordDesiredState("community_post", post.getId().toString(), "media", content.mediaUrls());
        replaceRelations(post.getId(), products, req.getTopics());
        return post.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(Long userId, CommunityPostUpdateReqVO req) {
        memberClient.validateActiveUserForUpdate(userId);
        CommunityPostDO current = requireOwnedForUpdate(userId, req.getId());
        if (!Objects.equals(current.getStatus(), CommunityPostStatusEnum.DRAFT.getStatus())
                && !Objects.equals(current.getStatus(), CommunityPostStatusEnum.HIDDEN.getStatus())) {
            throw exception(POST_STATE_INVALID);
        }
        NormalizedContent content = normalize(req.getTitle(), req.getContent(), req.getMediaUrls());
        List<Long> products = validateProducts(req.getProductIds());
        if (postMapper.updateOwnerFields(new CommunityPostDO().setId(req.getId()).setTitle(content.title())
                .setContent(content.content()).setMediaUrls(content.mediaUrls()), userId) != 1) {
            throw exception(POST_STATE_INVALID);
        }
        replaceRelations(req.getId(), products, req.getTopics());
        mediaOutboxService.recordDesiredState("community_post", req.getId().toString(), "media", content.mediaUrls());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPost(Long userId, Long id) {
        memberClient.validateActiveUserForUpdate(userId);
        CommunityPostDO post = requireOwnedForUpdate(userId, id);
        if (!Objects.equals(post.getStatus(), CommunityPostStatusEnum.DRAFT.getStatus())
                && !Objects.equals(post.getStatus(), CommunityPostStatusEnum.HIDDEN.getStatus())) {
            throw exception(POST_STATE_INVALID);
        }
        if (postMapper.updateStatus(id, post.getStatus(), CommunityPostStatusEnum.PUBLISHED.getStatus()) != 1) {
            throw exception(POST_STATE_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long userId, Long id) {
        memberClient.validateActiveUserForUpdate(userId);
        CommunityPostDO post = requireOwnedForUpdate(userId, id);
        if (postMapper.updateStatus(id, post.getStatus(), CommunityPostStatusEnum.HIDDEN.getStatus()) != 1) {
            throw exception(POST_STATE_INVALID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityPostRespVO getPost(Long viewerId, Long id) {
        CommunityPostDO post = postMapper.selectById(id);
        if (post == null || (!Objects.equals(post.getStatus(), CommunityPostStatusEnum.PUBLISHED.getStatus())
                && !Objects.equals(post.getAuthorUserId(), viewerId))) throw exception(POST_NOT_FOUND);
        return toPostResponse(post, viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CommunityPostRespVO> getFeed(Long viewerId, CommunityPostPageReqVO req) {
        PageResult<CommunityPostDO> page = postMapper.selectPublishedPage(req);
        return new PageResult<>(page.getList().stream().map(post -> toPostResponse(post, viewerId)).toList(), page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityTopicRespVO> getPopularTopics(Integer limit) {
        int safeLimit = Math.max(1, Math.min(Optional.ofNullable(limit).orElse(12), 50));
        return topicMapper.selectPopularTopics(safeLimit).stream().map(this::toTopic).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CommunityPostRespVO> getOwnerPosts(Long userId, CommunityPostOwnerPageReqVO req) {
        memberClient.validateActiveUser(userId);
        PageResult<CommunityPostDO> page = postMapper.selectOwnerPage(userId,
                new CommunityPostPageReqVO().setKeyword(req.getKeyword()));
        return new PageResult<>(page.getList().stream().map(post -> toPostResponse(post, userId)).toList(), page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CommunityPostRespVO> getFavoritePosts(Long userId, CommunityPostOwnerPageReqVO req) {
        memberClient.validateActiveUser(userId);
        PageResult<CommunityPostDO> page = postMapper.selectFavoritePage(userId, req);
        return new PageResult<>(page.getList().stream().map(post -> toPostResponse(post, userId)).toList(), page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CommunityPostRespVO> getFollowingPosts(Long userId, CommunityPostOwnerPageReqVO req) {
        memberClient.validateActiveUser(userId);
        PageResult<CommunityPostDO> page = postMapper.selectFollowingPage(userId, req);
        return new PageResult<>(page.getList().stream().map(post -> toPostResponse(post, userId)).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComment(Long userId, CommunityCommentCreateReqVO req) {
        memberClient.validateActiveUserForUpdate(userId);
        CommunityPostDO post = requirePublished(req.getPostId());
        Long parentId = req.getParentId();
        if (parentId != null) {
            CommunityCommentDO parent = commentMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getPostId(), post.getId())
                    || !Objects.equals(parent.getStatus(), CommunityCommentStatusEnum.VISIBLE.getStatus())) {
                throw exception(COMMENT_PARENT_INVALID);
            }
        }
        CommunityCommentDO comment = new CommunityCommentDO().setPostId(post.getId()).setParentId(parentId)
                .setAuthorUserId(userId).setContent(StrUtil.trim(req.getContent()))
                .setStatus(CommunityCommentStatusEnum.VISIBLE.getStatus());
        if (StrUtil.isBlank(comment.getContent())) throw exception(POST_CONTENT_INVALID);
        commentMapper.insert(comment);
        postMapper.incrementComment(post.getId(), 1);
        return comment.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CommunityCommentRespVO> getComments(Long viewerId, CommunityCommentPageReqVO req) {
        requirePublished(req.getPostId());
        PageResult<CommunityCommentDO> page = commentMapper.selectVisiblePage(req);
        return new PageResult<>(page.getList().stream().map(this::toCommentResponse).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setReaction(Long userId, CommunityReactionReqVO req) {
        memberClient.validateActiveUserForUpdate(userId);
        requirePublished(req.getPostId());
        if (!isReactionType(req.getType())) throw exception(REACTION_TYPE_INVALID);
        CommunityReactionDO existing = reactionMapper.selectOne(req.getPostId(), userId, req.getType());
        if (Boolean.TRUE.equals(req.getActive())) {
            if (existing != null) return;
            try {
                reactionMapper.insert(new CommunityReactionDO().setPostId(req.getPostId()).setUserId(userId).setType(req.getType()));
            } catch (DuplicateKeyException ignored) {
                return;
            }
            if (req.getType().equals(CommunityReactionTypeEnum.LIKE.getType())) postMapper.incrementLike(req.getPostId(), 1);
            else postMapper.incrementFavorite(req.getPostId(), 1);
        } else if (existing != null && reactionMapper.deleteOne(req.getPostId(), userId, req.getType()) > 0) {
            if (req.getType().equals(CommunityReactionTypeEnum.LIKE.getType())) postMapper.incrementLike(req.getPostId(), -1);
            else postMapper.incrementFavorite(req.getPostId(), -1);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setFollow(Long userId, CommunityFollowReqVO req) {
        memberClient.validateActiveUserForUpdate(userId);
        if (Objects.equals(userId, req.getUserId()) || memberClient.getUser(req.getUserId()) == null) {
            throw exception(FOLLOW_SELF_INVALID);
        }
        CommunityFollowDO existing = followMapper.selectOne(userId, req.getUserId());
        if (Boolean.TRUE.equals(req.getActive())) {
            if (existing != null) return;
            try {
                followMapper.insert(new CommunityFollowDO().setFollowerUserId(userId).setFollowedUserId(req.getUserId()));
            } catch (DuplicateKeyException ignored) { }
        } else if (existing != null) followMapper.deleteOne(userId, req.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long report(Long userId, CommunityReportCreateReqVO req) {
        memberClient.validateActiveUserForUpdate(userId);
        CommunityPostDO post = requirePublished(req.getPostId());
        String reason = StrUtil.trim(req.getReason());
        if (StrUtil.isBlank(reason)) throw exception(REPORT_INVALID);
        CommunityReportDO pending = reportMapper.selectPending(post.getId(), userId);
        if (pending != null) return pending.getId();
        CommunityReportDO report = new CommunityReportDO().setPostId(post.getId()).setReporterUserId(userId)
                .setReason(reason).setStatus(CommunityReportStatusEnum.PENDING.getStatus());
        reportMapper.insert(report);
        return report.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CommunityPostRespVO> getAdminPosts(CommunityPostAdminPageReqVO req) {
        PageResult<CommunityPostDO> page = postMapper.selectAdminPage(req);
        return new PageResult<>(page.getList().stream()
                .map(post -> toPostResponse(post, null)).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPostStatus(Long adminUserId, CommunityPostStatusReqVO req) {
        if (req.getStatus() < CommunityPostStatusEnum.DRAFT.getStatus()
                || req.getStatus() > CommunityPostStatusEnum.HIDDEN.getStatus()) throw exception(POST_STATE_INVALID);
        CommunityPostDO post = postMapper.selectByIdForUpdate(req.getId());
        if (post == null || postMapper.updateStatus(req.getId(), post.getStatus(), req.getStatus()) != 1) {
            throw exception(POST_STATE_INVALID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CommunityReportRespVO> getAdminReports(CommunityReportPageReqVO req) {
        PageResult<CommunityReportDO> page = reportMapper.selectAdminPage(req);
        return new PageResult<>(page.getList().stream().map(this::toReportResponse).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewReport(Long adminUserId, CommunityReportReviewReqVO req) {
        if (!Objects.equals(req.getStatus(), CommunityReportStatusEnum.RESOLVED.getStatus())
                && !Objects.equals(req.getStatus(), CommunityReportStatusEnum.REJECTED.getStatus())) throw exception(REPORT_STATE_INVALID);
        CommunityReportDO report = reportMapper.selectById(req.getId());
        if (report == null || !Objects.equals(report.getStatus(), CommunityReportStatusEnum.PENDING.getStatus())) throw exception(REPORT_STATE_INVALID);
        reportMapper.updateById(new CommunityReportDO().setId(req.getId()).setStatus(req.getStatus())
                .setReviewerUserId(adminUserId).setReviewRemark(StrUtil.trim(req.getRemark())).setReviewTime(LocalDateTime.now()));
        if (Objects.equals(req.getStatus(), CommunityReportStatusEnum.RESOLVED.getStatus())) {
            CommunityPostDO post = postMapper.selectById(report.getPostId());
            if (post != null) postMapper.updateStatus(post.getId(), post.getStatus(), CommunityPostStatusEnum.HIDDEN.getStatus());
        }
    }

    private CommunityPostDO requireOwnedForUpdate(Long userId, Long id) {
        CommunityPostDO post = postMapper.selectByIdForUpdate(id);
        if (post == null || !Objects.equals(post.getAuthorUserId(), userId)) throw exception(POST_NOT_FOUND);
        return post;
    }
    private CommunityPostDO requirePublished(Long id) {
        CommunityPostDO post = postMapper.selectById(id);
        if (post == null || !Objects.equals(post.getStatus(), CommunityPostStatusEnum.PUBLISHED.getStatus())) throw exception(POST_NOT_FOUND);
        return post;
    }
    private NormalizedContent normalize(String title, String content, List<String> mediaUrls) {
        String normalizedTitle = StrUtil.trim(title), normalizedContent = StrUtil.trim(content);
        if (StrUtil.isBlank(normalizedTitle) || StrUtil.isBlank(normalizedContent)) throw exception(POST_CONTENT_INVALID);
        List<String> images = mediaUrls == null ? List.of() : mediaUrls.stream().filter(Objects::nonNull)
                .map(StrUtil::trim).filter(StrUtil::isNotBlank).distinct().toList();
        return new NormalizedContent(normalizedTitle, normalizedContent, images);
    }
    private List<Long> validateProducts(List<Long> ids) {
        if (ids == null) return List.of();
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        for (Long id : distinct) if (productClient.getVisibleSummary(id) == null) throw exception(POST_PRODUCT_INVALID);
        return distinct;
    }
    private void replaceRelations(Long postId, List<Long> productIds, List<String> topicNames) {
        postProductMapper.deleteByPostId(postId);
        for (int i = 0; i < productIds.size(); i++) postProductMapper.insert(new CommunityPostProductDO().setPostId(postId)
                .setProductId(productIds.get(i)).setSort(i));
        postTopicMapper.deleteByPostId(postId);
        if (topicNames == null) return;
        Set<String> seen = new HashSet<>();
        for (String raw : topicNames) {
            String name = StrUtil.trim(raw);
            if (StrUtil.isBlank(name)) continue;
            if (name.length() > 120 || !seen.add(name)) throw exception(TOPIC_INVALID);
            String slug = slug(name);
            CommunityTopicDO topic = topicMapper.selectBySlug(slug);
            if (topic == null) {
                topic = new CommunityTopicDO().setName(name).setSlug(slug).setStatus(0);
                try { topicMapper.insert(topic); } catch (DuplicateKeyException ignored) { topic = topicMapper.selectBySlug(slug); }
            }
            if (topic == null) throw exception(TOPIC_INVALID);
            postTopicMapper.insert(new CommunityPostTopicDO().setPostId(postId).setTopicId(topic.getId()));
        }
    }
    private String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return StrUtil.isBlank(slug) ? "topic-" + Integer.toUnsignedString(name.hashCode()) : slug;
    }
    private boolean isReactionType(Integer type) { return Objects.equals(type, 1) || Objects.equals(type, 2); }
    private CommunityPostRespVO toPostResponse(CommunityPostDO post, Long viewerId) {
        CommunityPostRespVO response = new CommunityPostRespVO().setId(post.getId()).setAuthorUserId(post.getAuthorUserId())
                .setTitle(post.getTitle()).setContent(post.getContent()).setMediaUrls(post.getMediaUrls())
                .setStatus(post.getStatus()).setLikeCount(Optional.ofNullable(post.getLikeCount()).orElse(0))
                .setFavoriteCount(Optional.ofNullable(post.getFavoriteCount()).orElse(0))
                .setCommentCount(Optional.ofNullable(post.getCommentCount()).orElse(0)).setCreateTime(post.getCreateTime()).setUpdateTime(post.getUpdateTime());
        CommunityMemberProfile author = memberClient.getUser(post.getAuthorUserId());
        if (author != null) response.setAuthorNickname(author.getNickname()).setAuthorAvatar(author.getAvatar());
        List<CommunityPostTopicDO> postTopics = postTopicMapper.selectByPostId(post.getId());
        response.setTopics(postTopics.stream().map(item -> topicMapper.selectById(item.getTopicId())).filter(Objects::nonNull).map(this::toTopic).toList());
        response.setProducts(postProductMapper.selectByPostId(post.getId()).stream()
                .map(item -> productClient.getVisibleSummary(item.getProductId()))
                .filter(Objects::nonNull).toList());
        boolean logged = viewerId != null;
        response.setLiked(logged && reactionMapper.selectOne(post.getId(), viewerId, 1) != null)
                .setFavorited(logged && reactionMapper.selectOne(post.getId(), viewerId, 2) != null)
                .setFollowingAuthor(logged && followMapper.selectOne(viewerId, post.getAuthorUserId()) != null);
        return response;
    }
    private CommunityTopicRespVO toTopic(CommunityTopicDO topic) { return new CommunityTopicRespVO().setId(topic.getId()).setName(topic.getName()).setSlug(topic.getSlug()).setPostCount(topic.getPostCount()); }
    private CommunityCommentRespVO toCommentResponse(CommunityCommentDO comment) {
        CommunityCommentRespVO response = new CommunityCommentRespVO().setId(comment.getId()).setPostId(comment.getPostId()).setParentId(comment.getParentId()).setAuthorUserId(comment.getAuthorUserId()).setContent(comment.getContent()).setStatus(comment.getStatus()).setCreateTime(comment.getCreateTime());
        CommunityMemberProfile author = memberClient.getUser(comment.getAuthorUserId());
        if (author != null) response.setAuthorNickname(author.getNickname()).setAuthorAvatar(author.getAvatar());
        return response;
    }
    private CommunityReportRespVO toReportResponse(CommunityReportDO report) {
        CommunityReportRespVO response = new CommunityReportRespVO().setId(report.getId()).setPostId(report.getPostId())
                .setReporterUserId(report.getReporterUserId()).setReason(report.getReason()).setStatus(report.getStatus())
                .setReviewerUserId(report.getReviewerUserId()).setReviewRemark(report.getReviewRemark())
                .setReviewTime(report.getReviewTime()).setCreateTime(report.getCreateTime());
        CommunityPostDO post = postMapper.selectById(report.getPostId());
        if (post != null) {
            response.setPostTitle(post.getTitle()).setPostContent(post.getContent()).setPostMediaUrls(post.getMediaUrls())
                    .setPostAuthorUserId(post.getAuthorUserId());
            CommunityMemberProfile author = memberClient.getUser(post.getAuthorUserId());
            if (author != null) response.setPostAuthorNickname(author.getNickname());
        }
        CommunityMemberProfile reporter = memberClient.getUser(report.getReporterUserId());
        if (reporter != null) response.setReporterNickname(reporter.getNickname());
        return response;
    }
    private record NormalizedContent(String title, String content, List<String> mediaUrls) { }
}
