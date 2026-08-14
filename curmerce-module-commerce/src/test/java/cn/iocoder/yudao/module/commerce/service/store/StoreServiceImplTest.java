package cn.iocoder.yudao.module.commerce.service.store;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.STORE_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceImplTest {

    @Mock
    private MerchantAccessService merchantAccessService;
    @Mock
    private cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper storeMapper;
    @InjectMocks
    private StoreServiceImpl service;

    @Test
    void ownStore_propagatesAccessFailureWithoutStoreLookup() {
        when(merchantAccessService.requireApprovedOwner()).thenThrow(
                cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(STORE_ACCESS_DENIED));
        ServiceException error = assertThrows(ServiceException.class, service::getOwnStore);
        assertEquals(STORE_ACCESS_DENIED.getCode(), error.getCode());
        verifyNoInteractions(storeMapper);
    }

    @Test
    void ownStore_returnsServerResolvedStore() {
        MerchantDO merchant = new MerchantDO().setId(1L);
        StoreDO store = new StoreDO().setId(2L).setMerchantId(1L);
        when(merchantAccessService.requireApprovedOwner()).thenReturn(new MerchantAccessContext(merchant, store));
        assertEquals(store, service.getOwnStore());
        verifyNoInteractions(storeMapper);
    }

    @Test
    void updateOwnStore_usesServerResolvedOwnership() {
        MerchantDO merchant = new MerchantDO().setId(1L);
        StoreDO store = new StoreDO().setId(2L).setMerchantId(1L);
        when(merchantAccessService.requireApprovedOwner()).thenReturn(new MerchantAccessContext(merchant, store));
        when(storeMapper.updateOwned(eq(2L), eq(1L), any(StoreDO.class))).thenReturn(1);

        service.updateOwnStore(new cn.iocoder.yudao.module.commerce.controller.admin.store.vo.StoreUpdateOwnReqVO()
                .setName("Store").setContactName("Owner").setContactMobile("13800138000"));

        verify(storeMapper).updateOwned(eq(2L), eq(1L), any(StoreDO.class));
    }
}
