package cn.iocoder.yudao.curmerce.community.integration;

import cn.iocoder.yudao.curmerce.cloud.api.CoreMediaReferencesReqDTO;
import cn.iocoder.yudao.module.community.service.integration.CommunityMediaClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class RemoteCommunityMediaClient implements CommunityMediaClient {

    @Resource private CoreServiceHttpClient coreClient;

    @Override
    public void replaceFileReferences(String businessType, String businessId, String fieldName,
                                      Collection<String> urls) {
        coreClient.replaceMediaReferences(new CoreMediaReferencesReqDTO().setBusinessType(businessType)
                .setBusinessId(businessId).setFieldName(fieldName)
                .setUrls(urls == null ? List.of() : List.copyOf(urls)));
    }
}
