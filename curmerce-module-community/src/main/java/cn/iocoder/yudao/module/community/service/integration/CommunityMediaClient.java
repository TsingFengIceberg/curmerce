package cn.iocoder.yudao.module.community.service.integration;

import java.util.Collection;

public interface CommunityMediaClient {
    void replaceFileReferences(String businessType, String businessId, String fieldName, Collection<String> urls);
}
