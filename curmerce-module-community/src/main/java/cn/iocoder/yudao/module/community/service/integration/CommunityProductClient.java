package cn.iocoder.yudao.module.community.service.integration;

import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityProductSummaryRespVO;

public interface CommunityProductClient {
    CommunityProductSummaryRespVO getVisibleSummary(Long productId);
}
