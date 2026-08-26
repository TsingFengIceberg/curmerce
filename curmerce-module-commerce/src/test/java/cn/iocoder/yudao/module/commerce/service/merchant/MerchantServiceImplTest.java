package cn.iocoder.yudao.module.commerce.service.merchant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantApproveReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantRejectReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantOperatorMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserProvisionReqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.MERCHANT_NOT_PENDING;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.MERCHANT_REVIEW_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceImplTest {
    @Mock private MerchantMapper merchantMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private MerchantOperatorMapper operatorMapper;
    @Mock private AdminUserApi adminUserApi;
    @InjectMocks private MerchantServiceImpl service;

    @Test
    void createMerchant_startsPendingAndReservesCodes() {
        when(merchantMapper.selectByCode("merchant_a")).thenReturn(null);
        when(merchantMapper.selectByDefaultStoreCode("store_a")).thenReturn(null);
        when(storeMapper.selectByCode("store_a")).thenReturn(null);
        MerchantCreateReqVO request = new MerchantCreateReqVO().setName(" Merchant A ").setCode("merchant_a")
                .setContactName("Alice").setContactMobile("13800138000").setDefaultStoreName("Store A")
                .setDefaultStoreCode("store_a");
        Long id = service.createMerchant(request);
        verify(merchantMapper).insert((MerchantDO) argThat((MerchantDO m) -> "Merchant A".equals(m.getName())
                && MerchantAuditStatusEnum.PENDING.getStatus().equals(m.getStatus())));
    }

    @Test
    void approveMerchant_provisionsAggregateAtomically() {
        MerchantDO pending = new MerchantDO().setId(1L).setDefaultStoreName("Store A")
                .setDefaultStoreCode("store_a").setContactName("Alice").setContactMobile("13800138000")
                .setStatus(MerchantAuditStatusEnum.PENDING.getStatus());
        when(merchantMapper.selectPendingForUpdate(1L)).thenReturn(pending);
        when(storeMapper.selectByCode("store_a")).thenReturn(null);
        when(adminUserApi.provisionUser(any())).thenReturn(9L);
        when(merchantMapper.updateReview(anyLong(), anyInt(), anyInt(), anyLong(), any(), anyLong(), isNull())).thenReturn(1);
        withLoginUser(2L, () -> {
            service.approveMerchant(new MerchantApproveReqVO().setId(1L).setUsername("owner01")
                    .setNickname("Owner").setPassword("secret-123"));
        });
        verify(adminUserApi).provisionUser(argThat(req -> "merchant_owner".equals(req.getRoleCode())));
        verify(storeMapper).insert((cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO) any());
        verify(operatorMapper).insert((cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO)
                argThat((cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO op) ->
                        Long.valueOf(9L).equals(op.getUserId())));
        verify(merchantMapper).updateReview(eq(1L), eq(0), eq(1), eq(2L), any(), eq(9L), isNull());
    }

    @Test
    void reviewTerminalState_isRejected() {
        MerchantDO approved = new MerchantDO().setId(1L).setStatus(MerchantAuditStatusEnum.APPROVED.getStatus());
        when(merchantMapper.selectPendingForUpdate(1L)).thenReturn(approved);
        ServiceException error = assertThrows(ServiceException.class, () -> service.rejectMerchant(
                new MerchantRejectReqVO().setId(1L).setReason("not eligible")));
        assertEquals(MERCHANT_NOT_PENDING.getCode(), error.getCode());
        verifyNoInteractions(adminUserApi, storeMapper, operatorMapper);
    }

    @Test
    void approvalConflict_doesNotPublishTerminalState() {
        MerchantDO pending = new MerchantDO().setId(1L).setDefaultStoreName("Store A")
                .setDefaultStoreCode("store_a").setContactName("Alice").setContactMobile("13800138000")
                .setStatus(MerchantAuditStatusEnum.PENDING.getStatus());
        when(merchantMapper.selectPendingForUpdate(1L)).thenReturn(pending);
        when(storeMapper.selectByCode("store_a")).thenReturn(null);
        when(adminUserApi.provisionUser(any(AdminUserProvisionReqDTO.class))).thenReturn(9L);
        when(merchantMapper.updateReview(anyLong(), anyInt(), anyInt(), anyLong(), any(), anyLong(), isNull()))
                .thenReturn(0);

        withLoginUser(2L, () -> {
            ServiceException error = assertThrows(ServiceException.class, () -> service.approveMerchant(
                    new MerchantApproveReqVO().setId(1L).setUsername("owner01")
                            .setNickname("Owner").setPassword("secret-123")));
            assertEquals(MERCHANT_REVIEW_CONFLICT.getCode(), error.getCode());
        });
        verify(merchantMapper).updateReview(eq(1L), eq(0), eq(1), eq(2L), any(), eq(9L), isNull());
    }

    @Test
    void secondReviewAfterFirstTerminalState_isRejectedWithoutProvisioning() {
        MerchantDO approved = new MerchantDO().setId(1L).setStatus(MerchantAuditStatusEnum.APPROVED.getStatus());
        when(merchantMapper.selectPendingForUpdate(1L)).thenReturn(approved);
        ServiceException error = assertThrows(ServiceException.class, () -> service.approveMerchant(
                new MerchantApproveReqVO().setId(1L).setUsername("owner01")
                        .setNickname("Owner").setPassword("secret-123")));
        assertEquals(MERCHANT_NOT_PENDING.getCode(), error.getCode());
        verifyNoInteractions(adminUserApi, storeMapper, operatorMapper);
    }

    private void withLoginUser(Long userId, Runnable action) {
        try {
            LoginUser loginUser = new LoginUser().setId(userId);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
