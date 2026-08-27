package cn.iocoder.yudao.curmerce.community.integration;

import cn.iocoder.yudao.curmerce.cloud.api.CoreMemberUserRespDTO;
import cn.iocoder.yudao.module.community.service.integration.CommunityMemberClient;
import cn.iocoder.yudao.module.community.service.integration.CommunityMemberProfile;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class RemoteCommunityMemberClient implements CommunityMemberClient {

    @Resource private CoreServiceHttpClient coreClient;

    @Override
    public CommunityMemberProfile getUser(Long id) {
        CoreMemberUserRespDTO user = coreClient.getMember(id);
        return user == null ? null : new CommunityMemberProfile().setId(user.getId())
                .setNickname(user.getNickname()).setAvatar(user.getAvatar()).setStatus(user.getStatus());
    }

    @Override
    public void validateActiveUser(Long id) {
        coreClient.validateMember(id, false);
    }

    @Override
    public void validateActiveUserForUpdate(Long id) {
        coreClient.validateMember(id, true);
    }
}
