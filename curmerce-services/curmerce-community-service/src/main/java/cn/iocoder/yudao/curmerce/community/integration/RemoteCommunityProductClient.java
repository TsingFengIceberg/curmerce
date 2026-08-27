package cn.iocoder.yudao.curmerce.community.integration;

import cn.iocoder.yudao.curmerce.cloud.api.CoreProductSummaryRespDTO;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityProductSummaryRespVO;
import cn.iocoder.yudao.module.community.service.integration.CommunityProductClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class RemoteCommunityProductClient implements CommunityProductClient {

    @Resource private CoreServiceHttpClient coreClient;

    @Override
    public CommunityProductSummaryRespVO getVisibleSummary(Long productId) {
        CoreProductSummaryRespDTO source = coreClient.getVisibleProduct(productId);
        if (source == null) {
            return null;
        }
        return new CommunityProductSummaryRespVO().setId(source.getId()).setCategoryId(source.getCategoryId())
                .setStoreId(source.getStoreId()).setStoreName(source.getStoreName())
                .setSellerType(source.getSellerType()).setSellerUserId(source.getSellerUserId())
                .setSellerName(source.getSellerName()).setName(source.getName()).setCondition(source.getCondition())
                .setSubtitle(source.getSubtitle()).setMainImageUrl(source.getMainImageUrl())
                .setMinPrice(source.getMinPrice()).setMinMarketPrice(source.getMinMarketPrice())
                .setTotalStock(source.getTotalStock()).setAvailable(source.getAvailable());
    }
}
