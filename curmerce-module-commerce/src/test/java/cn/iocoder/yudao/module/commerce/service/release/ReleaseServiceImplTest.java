package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleaseCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleaseUpdateReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseCampaignDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseItemDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseCampaignMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseItemMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleasePurchaseMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseReservationMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.RELEASE_PURCHASE_DUPLICATE;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.RELEASE_NOT_FOUND;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.RELEASE_STATE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceImplTest {
    @Mock private CommerceReleaseCampaignMapper campaignMapper;
    @Mock private CommerceReleaseItemMapper itemMapper;
    @Mock private CommerceReleasePurchaseMapper purchaseMapper;
    @Mock private CommerceReleaseReservationMapper reservationMapper;
    @Mock private CommerceOrderMapper orderMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductSkuMapper skuMapper;
    @Mock private MerchantAccessService merchantAccessService;
    @Mock private MemberUserApi memberUserApi;
    @Mock private OrderService orderService;
    @Mock private ReleaseReservationService reservationService;
    @InjectMocks private ReleaseServiceImpl service;

    @Test
    void update_replacesItemsForOwnedDraft() {
        MerchantAccessContext context = merchantContext();
        when(merchantAccessService.requireApprovedOwner()).thenReturn(context);
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setMerchantId(99L).setStoreId(199L).setStatus(0));
        when(productMapper.selectByIdAndMerchantId(201L, 99L)).thenReturn(new cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO()
                .setId(201L).setMerchantId(99L).setStoreId(199L));
        when(skuMapper.selectByIdAndProductIdForUpdate(301L, 201L)).thenReturn(new cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO()
                .setId(301L).setProductId(201L).setMerchantId(99L).setStock(5));

        service.update(updateRequest());

        verify(campaignMapper).updateById(argThat((CommerceReleaseCampaignDO campaign) -> campaign.getId().equals(10L)
                && campaign.getName().equals("Updated release") && campaign.getPerUserLimit().equals(2)));
        verify(itemMapper).deleteByCampaignId(10L);
        verify(itemMapper).insert(argThat((CommerceReleaseItemDO item) -> item.getCampaignId().equals(10L)
                && item.getProductId().equals(201L) && item.getSkuId().equals(301L)
                && item.getCampaignPrice().equals(800L) && item.getStock().equals(3)));
    }

    @Test
    void update_rejectsNonDraft() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(merchantContext());
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setMerchantId(99L).setStoreId(199L).setStatus(10));

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.update(updateRequest()));

        assertEquals(RELEASE_STATE_INVALID.getCode(), error.getCode());
        verify(campaignMapper, never()).updateById(any(CommerceReleaseCampaignDO.class));
        verify(itemMapper, never()).deleteByCampaignId(anyLong());
    }

    @Test
    void update_rejectsOtherMerchantCampaign() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(merchantContext());
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setMerchantId(100L).setStoreId(200L).setStatus(0));

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.update(updateRequest()));

        assertEquals(RELEASE_NOT_FOUND.getCode(), error.getCode());
        verify(campaignMapper, never()).updateById(any(CommerceReleaseCampaignDO.class));
    }

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

    @Test
    void purchase_returnsCommittedResultWhenAStreamMessageIsRedeliveredAfterAckFailure() {
        CommerceReleaseItemDO item = new CommerceReleaseItemDO().setId(20L).setCampaignId(10L)
                .setProductId(201L).setSkuId(301L).setStock(0).setCampaignPrice(199L);
        when(itemMapper.selectByIdForUpdate(20L)).thenReturn(item);
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setStatus(20).setStartTime(LocalDateTime.now().minusMinutes(1)).setEndTime(LocalDateTime.now().plusMinutes(10))
                .setPerUserLimit(1));
        when(purchaseMapper.selectByBuyerAndItem(7L, 20L)).thenReturn(
                new cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseDO()
                        .setId(30L).setCampaignId(10L).setItemId(20L).setQuantity(1).setUnitPrice(199L).setOrderId(900L));
        when(orderMapper.selectById(900L)).thenReturn(new CommerceOrderDO().setId(900L)
                .setOrderNo("C-900").setIdempotencyKey("release-key").setStatus(10));

        var response = service.purchase(7L, new ReleasePurchaseReqVO().setItemId(20L).setQuantity(1)
                .setAddressId(701L).setIdempotencyKey("release-key"));

        assertEquals(30L, response.getPurchaseId());
        assertEquals(900L, response.getOrderId());
        verify(itemMapper, never()).updateInventory(anyLong(), anyInt());
        verify(orderService, never()).createReleaseOrder(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void purchase_returnsCommittedResultWhenSameKeyCompletesWhileWaitingForItemLock() {
        CommerceReleaseItemDO item = new CommerceReleaseItemDO().setId(20L).setCampaignId(10L)
                .setProductId(201L).setSkuId(301L).setStock(1).setCampaignPrice(199L);
        CommerceReleaseCampaignDO campaign = new CommerceReleaseCampaignDO().setId(10L).setStatus(20)
                .setStartTime(LocalDateTime.now().minusMinutes(1)).setEndTime(LocalDateTime.now().plusMinutes(10))
                .setPerUserLimit(1);
        CommerceReleasePurchaseDO committedPurchase = new CommerceReleasePurchaseDO().setId(30L).setCampaignId(10L)
                .setItemId(20L).setQuantity(1).setUnitPrice(199L).setOrderId(900L);
        when(itemMapper.selectByIdForUpdate(20L)).thenReturn(item);
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(campaign);
        // The first read happens before Redis/item-lock contention; the second
        // read sees the winner after this request receives the row lock.
        when(purchaseMapper.selectByBuyerAndItem(7L, 20L)).thenReturn(null, committedPurchase);
        when(orderMapper.selectById(900L)).thenReturn(new CommerceOrderDO().setId(900L)
                .setOrderNo("C-900").setIdempotencyKey("release-key").setStatus(10));

        var response = service.purchase(7L, new ReleasePurchaseReqVO().setItemId(20L).setQuantity(1)
                .setAddressId(701L).setIdempotencyKey("release-key"));

        assertEquals(30L, response.getPurchaseId());
        assertEquals(900L, response.getOrderId());
        verify(itemMapper, never()).updateInventory(anyLong(), anyInt());
        verify(orderService, never()).createReleaseOrder(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void purchase_sameKeyRetryAdoptsReservationForCommitButDoesNotTreatItAsOwnedOnRollback() {
        when(reservationService.reservationKey("release-key")).thenReturn("release-key");
        when(reservationService.reserveTracked(10L, 20L, 7L, 1, 1, 1, "release-key"))
                .thenReturn(ReleaseReservationService.ReservationResult.ALREADY_RESERVED);
        when(reservationService.commitTracked(10L, 20L, 7L, 1, "release-key")).thenReturn(true);
        when(itemMapper.selectById(20L)).thenReturn(new CommerceReleaseItemDO().setId(20L).setCampaignId(10L)
                .setProductId(201L).setSkuId(301L).setStock(1).setCampaignPrice(199L));
        when(campaignMapper.selectById(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setStatus(20).setStartTime(LocalDateTime.now().minusMinutes(1)).setEndTime(LocalDateTime.now().plusMinutes(10))
                .setPerUserLimit(1));
        when(itemMapper.selectByIdForUpdate(20L)).thenReturn(new CommerceReleaseItemDO().setId(20L).setCampaignId(10L)
                .setProductId(201L).setSkuId(301L).setStock(1).setCampaignPrice(199L));
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setStatus(20).setStartTime(LocalDateTime.now().minusMinutes(1)).setEndTime(LocalDateTime.now().plusMinutes(10))
                .setPerUserLimit(1));
        when(purchaseMapper.selectByBuyerAndItem(7L, 20L)).thenReturn(null);
        when(itemMapper.updateInventory(20L, 1)).thenReturn(1);
        when(orderService.createReleaseOrder(eq(7L), eq(701L), eq(201L), eq(301L), eq(199L), eq(1), eq("release-key")))
                .thenReturn(new OrderCreateRespVO().setOrderId(900L).setOrderNo("C-900").setStatus(10));
        when(purchaseMapper.insert(any(CommerceReleasePurchaseDO.class))).thenAnswer(invocation -> {
            invocation.<CommerceReleasePurchaseDO>getArgument(0).setId(30L);
            return 1;
        });
        when(reservationMapper.insert(any(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO.class))).thenReturn(1);

        var response = service.purchase(7L, new ReleasePurchaseReqVO().setItemId(20L).setQuantity(1)
                .setAddressId(701L).setIdempotencyKey("release-key"));

        assertEquals(900L, response.getOrderId());
        verify(reservationMapper).insert(argThat((CommerceReleaseReservationDO row) -> row.getReservationKey().equals("release-key")
                && row.getStatus().equals(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO.COMMITTED)));
        verify(reservationService).commitTracked(10L, 20L, 7L, 1, "release-key");
        verify(reservationService, never()).releaseTracked(anyLong(), anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void purchase_alreadyReservedDoesNotReleaseConcurrentOwnerWhenTransactionRollsBack() {
        when(reservationService.reservationKey("rollback-key")).thenReturn("rollback-key");
        when(reservationService.reserveTracked(10L, 20L, 7L, 1, 1, 1, "rollback-key"))
                .thenReturn(ReleaseReservationService.ReservationResult.ALREADY_RESERVED);
        when(itemMapper.selectById(20L)).thenReturn(new CommerceReleaseItemDO().setId(20L).setCampaignId(10L)
                .setProductId(201L).setSkuId(301L).setStock(1).setCampaignPrice(199L));
        when(campaignMapper.selectById(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setStatus(20).setStartTime(LocalDateTime.now().minusMinutes(1)).setEndTime(LocalDateTime.now().plusMinutes(10))
                .setPerUserLimit(1));
        when(itemMapper.selectByIdForUpdate(20L)).thenReturn(new CommerceReleaseItemDO().setId(20L).setCampaignId(10L)
                .setProductId(201L).setSkuId(301L).setStock(1).setCampaignPrice(199L));
        when(campaignMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceReleaseCampaignDO().setId(10L)
                .setStatus(20).setStartTime(LocalDateTime.now().minusMinutes(1)).setEndTime(LocalDateTime.now().plusMinutes(10))
                .setPerUserLimit(1));
        when(purchaseMapper.selectByBuyerAndItem(7L, 20L)).thenReturn(null);
        when(itemMapper.updateInventory(20L, 1)).thenReturn(1);
        when(orderService.createReleaseOrder(eq(7L), eq(701L), eq(201L), eq(301L), eq(199L), eq(1), eq("rollback-key")))
                .thenReturn(new OrderCreateRespVO().setOrderId(901L).setOrderNo("C-901").setStatus(10));
        when(purchaseMapper.insert(any(CommerceReleasePurchaseDO.class))).thenAnswer(invocation -> {
            invocation.<CommerceReleasePurchaseDO>getArgument(0).setId(31L);
            return 1;
        });
        when(reservationMapper.insert(any(cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO.class))).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.purchase(7L, new ReleasePurchaseReqVO().setItemId(20L).setQuantity(1)
                    .setAddressId(701L).setIdempotencyKey("rollback-key"));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(reservationService, never()).releaseTracked(anyLong(), anyLong(), anyLong(), anyInt(), eq("rollback-key"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private MerchantAccessContext merchantContext() {
        return new MerchantAccessContext(new MerchantDO().setId(99L), new StoreDO().setId(199L).setMerchantId(99L));
    }

    private ReleaseUpdateReqVO updateRequest() {
        ReleaseCreateReqVO.Item item = new ReleaseCreateReqVO.Item().setProductId(201L).setSkuId(301L)
                .setCampaignPrice(800L).setStock(3);
        return (ReleaseUpdateReqVO) new ReleaseUpdateReqVO().setId(10L).setName(" Updated release ")
                .setStartTime(LocalDateTime.now().plusHours(1)).setEndTime(LocalDateTime.now().plusHours(2))
                .setPerUserLimit(2).setItems(java.util.List.of(item));
    }
}
