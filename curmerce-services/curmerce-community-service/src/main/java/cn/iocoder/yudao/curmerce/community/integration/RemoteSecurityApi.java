package cn.iocoder.yudao.curmerce.community.integration;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static cn.iocoder.yudao.curmerce.cloud.api.CorePermissionCheckReqDTO.TYPE_PERMISSION;
import static cn.iocoder.yudao.curmerce.cloud.api.CorePermissionCheckReqDTO.TYPE_ROLE;

@Component
public class RemoteSecurityApi implements OAuth2TokenCommonApi, PermissionCommonApi {

    @Resource private CoreServiceHttpClient coreClient;

    @Override
    public OAuth2AccessTokenRespDTO createAccessToken(OAuth2AccessTokenCreateReqDTO reqDTO) {
        throw new UnsupportedOperationException("community service cannot issue access tokens");
    }

    @Override
    public OAuth2AccessTokenCheckRespDTO checkAccessToken(String accessToken) {
        return coreClient.checkToken(accessToken);
    }

    @Override
    public OAuth2AccessTokenRespDTO removeAccessToken(String accessToken) {
        throw new UnsupportedOperationException("community service cannot revoke access tokens");
    }

    @Override
    public OAuth2AccessTokenRespDTO refreshAccessToken(String refreshToken, String clientId) {
        throw new UnsupportedOperationException("community service cannot refresh access tokens");
    }

    @Override
    public boolean hasAnyPermissions(Long userId, String... permissions) {
        return coreClient.checkPermission(userId, TYPE_PERMISSION, Arrays.asList(permissions));
    }

    @Override
    public boolean hasAnyRoles(Long userId, String... roles) {
        return coreClient.checkPermission(userId, TYPE_ROLE, Arrays.asList(roles));
    }

    @Override
    public DeptDataPermissionRespDTO getDeptDataPermission(Long userId) {
        return coreClient.getDeptDataPermission(userId);
    }
}
