package cn.iocoder.yudao.module.commerce.service.merchant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantOperatorMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.commerce.enums.merchant.MerchantOperatorTypeEnum.OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantAccessServiceImplTest {

    @Mock private MerchantOperatorMapper operatorMapper;
    @Mock private MerchantMapper merchantMapper;
    @Mock private StoreMapper storeMapper;
    @InjectMocks private MerchantAccessServiceImpl service;

    @Test
    void rejectsMissingLogin() {
        try (MockedStatic<cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils> security =
                     mockStatic(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId)
                    .thenReturn(null);
            assertCode(STORE_ACCESS_DENIED);
        }
        verifyNoInteractions(operatorMapper, merchantMapper, storeMapper);
    }

    @Test
    void rejectsMissingOwnerRelation() {
        when(operatorMapper.selectListByUserId(5L)).thenReturn(List.of());
        withLoginUser(5L, () -> assertCode(MERCHANT_OPERATOR_NOT_EXISTS));
    }

    @Test
    void rejectsAmbiguousOwnerRelations() {
        when(operatorMapper.selectListByUserId(5L)).thenReturn(List.of(
                new MerchantOperatorDO().setMerchantId(1L).setOperatorType(OWNER.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus()),
                new MerchantOperatorDO().setMerchantId(2L).setOperatorType(OWNER.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus())));
        withLoginUser(5L, () -> assertCode(MERCHANT_OPERATOR_AMBIGUOUS));
        verifyNoInteractions(merchantMapper, storeMapper);
    }

    @Test
    void ignoresNonOwnerRelationsAndRejectsInactiveOwner() {
        when(operatorMapper.selectListByUserId(5L)).thenReturn(List.of(
                new MerchantOperatorDO().setMerchantId(1L).setOperatorType(99).setStatus(CommonStatusEnum.ENABLE.getStatus()),
                new MerchantOperatorDO().setMerchantId(2L).setOperatorType(OWNER.getType()).setStatus(CommonStatusEnum.DISABLE.getStatus())));
        withLoginUser(5L, () -> assertCode(MERCHANT_OPERATOR_NOT_ACTIVE));
    }

    @Test
    void rejectsUnapprovedOrMissingMerchant() {
        when(operatorMapper.selectListByUserId(5L)).thenReturn(List.of(
                new MerchantOperatorDO().setMerchantId(2L).setOperatorType(OWNER.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus())));
        when(merchantMapper.selectById(2L)).thenReturn(new MerchantDO().setId(2L)
                .setStatus(MerchantAuditStatusEnum.PENDING.getStatus()));
        withLoginUser(5L, () -> assertCode(STORE_ACCESS_DENIED));
    }

    @Test
    void resolvesApprovedMerchantAndStoreFromServer() {
        when(operatorMapper.selectListByUserId(5L)).thenReturn(List.of(
                new MerchantOperatorDO().setMerchantId(2L).setOperatorType(OWNER.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus())));
        MerchantDO merchant = new MerchantDO().setId(2L).setStatus(MerchantAuditStatusEnum.APPROVED.getStatus());
        StoreDO store = new StoreDO().setId(3L).setMerchantId(2L);
        when(merchantMapper.selectById(2L)).thenReturn(merchant);
        when(storeMapper.selectByMerchantId(2L)).thenReturn(store);
        withLoginUser(5L, () -> assertEquals(new MerchantAccessContext(merchant, store), service.requireApprovedOwner()));
    }

    @Test
    void rejectsMissingStore() {
        when(operatorMapper.selectListByUserId(5L)).thenReturn(List.of(
                new MerchantOperatorDO().setMerchantId(2L).setOperatorType(OWNER.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus())));
        when(merchantMapper.selectById(2L)).thenReturn(new MerchantDO().setId(2L)
                .setStatus(MerchantAuditStatusEnum.APPROVED.getStatus()));
        when(storeMapper.selectByMerchantId(2L)).thenReturn(null);
        withLoginUser(5L, () -> assertCode(STORE_NOT_EXISTS));
    }

    private void assertCode(cn.iocoder.yudao.framework.common.exception.ErrorCode expected) {
        ServiceException error = assertThrows(ServiceException.class, service::requireApprovedOwner);
        assertEquals(expected.getCode(), error.getCode());
    }

    private void withLoginUser(Long userId, Runnable action) {
        try (MockedStatic<cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils> security =
                     mockStatic(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);
            action.run();
        }
    }
}
