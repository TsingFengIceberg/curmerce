package cn.iocoder.yudao.module.community.service.integration;

public interface CommunityMemberClient {
    CommunityMemberProfile getUser(Long id);
    void validateActiveUser(Long id);
    void validateActiveUserForUpdate(Long id);
}
