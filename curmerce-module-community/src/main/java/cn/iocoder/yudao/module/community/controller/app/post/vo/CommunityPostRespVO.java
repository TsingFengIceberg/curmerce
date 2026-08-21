package cn.iocoder.yudao.module.community.controller.app.post.vo;

import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.PublicProductSummaryRespVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommunityPostRespVO {
    private Long id;
    private Long authorUserId;
    private String authorNickname;
    private String authorAvatar;
    private String title;
    private String content;
    private List<String> mediaUrls;
    private Integer status;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Boolean liked;
    private Boolean favorited;
    private Boolean followingAuthor;
    private List<CommunityTopicRespVO> topics;
    private List<PublicProductSummaryRespVO> products;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
