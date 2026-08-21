package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseCampaignDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseItemDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseCampaignMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseItemMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleasePurchaseMapper;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import cn.iocoder.yudao.module.commerce.controller.app.order.vo.OrderCreateRespVO;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.RELEASE_PURCHASE_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceImplTest {
    @Mock private CommerceReleaseCampaignMapper campaignMapper;
    @Mock private CommerceReleaseItemMapper itemMapper;
    @Mock private CommerceReleasePurchaseMapper purchaseMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductSkuMapper skuMapper;
    @Mock private MerchantAccessService merchantAccessService;
    @Mock private MemberUserApi memberUserApi;
    @Mock private OrderService orderService;
    @InjectMocks private ReleaseServiceImpl service;

    @Test
    void purchase_deductsCampaignInventoryAndCreatesRecord() {
        when(itemMapper.selectByIdForUpdate(20L)).thenReturn(new CommerceReleaseItemDO().setId(20L).setCampaignId(10L)
                .setProductId(201L).setSkuId(301L).setStock(8).setCampaignPrice(199L));
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setStatus(20).setStartTime(LocalDateTime.now().minusMinutes(1)).setEndTime(LocalDateTime.now().plusMinutes(10)).setPerUserLimit(2));
        when(purchaseMapper.selectByBuyerAndItem(7L, 20L)).thenReturn(null);
        when(itemMapper.updateInventory(20L, 2)).thenReturn(1);
        when(orderService.createReleaseOrder(eq(7L), eq(701L), anyLong(), anyLong(), eq(199L), eq(2), eq("release-key")))
                .thenReturn(new OrderCreateRespVO().setOrderId(900L).setOrderNo("C-900").setStatus(10));
        when(purchaseMapper.insert(any(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseDO.class))).thenAnswer(invocation -> { invocation.<cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseDO>getArgument(0).setId(30L); return 1; });

        var response = service.purchase(7L, new ReleasePurchaseReqVO().setItemId(20L).setQuantity(2)
                .setAddressId(701L).setIdempotencyKey("release-key"));

        assertEquals(30L, response.getPurchaseId());
        assertEquals(900L, response.getOrderId());
        verify(itemMapper).updateInventory(20L, 2);
    }

    @Test
    void purchase_rejectsRepeatedBuyerItem() {
        when(itemMapper.selectByIdForUpdate(20L)).thenReturn(new CommerceReleaseItemDO().setId(20L).setCampaignId(10L));
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L).setStatus(20)
                .setStartTime(LocalDateTime.now().minusMinutes(1)).setEndTime(LocalDateTime.now().plusMinutes(10)).setPerUserLimit(2));
        when(purchaseMapper.selectByBuyerAndItem(7L, 20L)).thenReturn(new cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseDO().setId(30L));

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.purchase(7L, new ReleasePurchaseReqVO().setItemId(20L).setQuantity(1)
                        .setAddressId(701L).setIdempotencyKey("release-key")));
        assertEquals(RELEASE_PURCHASE_DUPLICATE.getCode(), error.getCode());
        verify(itemMapper, never()).updateInventory(anyLong(), anyInt());
    }
}
