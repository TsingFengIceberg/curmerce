package cn.iocoder.yudao.module.commerce.service.store;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantOperatorMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.MERCHANT_OPERATOR_AMBIGUOUS;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.STORE_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceImplTest {
    @Mock private MerchantOperatorMapper operatorMapper;
    @Mock private MerchantMapper merchantMapper;
    @Mock private StoreMapper storeMapper;
    @InjectMocks private StoreServiceImpl service;

    @Test
    void ownStore_failsClosedWithoutLogin() {
        try (MockedStatic<cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils> security =
                     mockStatic(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            ServiceException error = assertThrows(ServiceException.class, service::getOwnStore);
            assertEquals(STORE_ACCESS_DENIED.getCode(), error.getCode());
        }
        verifyNoInteractions(operatorMapper, merchantMapper, storeMapper);
    }

    @Test
    void ownStore_rejectsAmbiguousOwnerRelations() {
        when(operatorMapper.selectListByUserId(5L)).thenReturn(List.of(
                new MerchantOperatorDO().setMerchantId(1L).setOperatorType(1).setStatus(0),
                new MerchantOperatorDO().setMerchantId(2L).setOperatorType(1).setStatus(0)));
        try (MockedStatic<cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils> security =
                     mockStatic(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId).thenReturn(5L);
            ServiceException error = assertThrows(ServiceException.class, service::getOwnStore);
            assertEquals(MERCHANT_OPERATOR_AMBIGUOUS.getCode(), error.getCode());
        }
        verifyNoInteractions(merchantMapper, storeMapper);
    }
}
