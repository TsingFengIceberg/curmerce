package cn.iocoder.yudao.module.commerce.service.auction;

import cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionBidReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.auction.vo.AuctionUpdateReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionBidDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionSessionDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.auction.CommerceAuctionBidMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.auction.CommerceAuctionSessionMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.enums.auction.AuctionStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.AUCTION_BID_INVALID;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.AUCTION_NOT_FOUND;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.AUCTION_STATE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceImplTest {
    @Mock private CommerceAuctionSessionMapper sessionMapper;
    @Mock private CommerceAuctionBidMapper bidMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductSkuMapper skuMapper;
    @Mock private MerchantAccessService merchantAccessService;
    @Mock private MemberUserApi memberUserApi;
    @Mock private OrderService orderService;
    @Mock private CommerceOrderMapper orderMapper;
    @InjectMocks private AuctionServiceImpl service;

    @Test
    void update_updatesOwnedDraft() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(merchantContext());
        when(sessionMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceAuctionSessionDO().setId(10L)
                .setMerchantId(99L).setStoreId(199L).setStatus(AuctionStatusEnum.DRAFT.getStatus()));
        when(productMapper.selectByIdAndMerchantId(201L, 99L)).thenReturn(new cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO()
                .setId(201L).setMerchantId(99L).setStoreId(199L));
        when(skuMapper.selectByIdAndProductIdForUpdate(301L, 201L)).thenReturn(new cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO()
                .setId(301L).setProductId(201L).setMerchantId(99L).setStock(1));

        service.update(updateRequest());

        verify(sessionMapper).updateById(argThat((CommerceAuctionSessionDO session) -> session.getId().equals(10L)
                && session.getName().equals("Updated auction") && session.getProductId().equals(201L)
                && session.getSkuId().equals(301L) && session.getStartingPrice().equals(1000L)
                && session.getMinIncrement().equals(100L)));
    }

    @Test
    void update_rejectsNonDraft() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(merchantContext());
        when(sessionMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceAuctionSessionDO().setId(10L)
                .setMerchantId(99L).setStoreId(199L).setStatus(AuctionStatusEnum.SCHEDULED.getStatus()));

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.update(updateRequest()));

        assertEquals(AUCTION_STATE_INVALID.getCode(), error.getCode());
        verify(sessionMapper, never()).updateById(any(CommerceAuctionSessionDO.class));
    }

    @Test
    void update_rejectsOtherMerchantSession() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(merchantContext());
        when(sessionMapper.selectByIdForUpdate(10L)).thenReturn(new CommerceAuctionSessionDO().setId(10L)
                .setMerchantId(100L).setStoreId(200L).setStatus(AuctionStatusEnum.DRAFT.getStatus()));

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.update(updateRequest()));

        assertEquals(AUCTION_NOT_FOUND.getCode(), error.getCode());
        verify(sessionMapper, never()).updateById(any(CommerceAuctionSessionDO.class));
    }

    @Test
    void bid_acceptsMinimumOpeningBidAndMovesScheduledSessionToRunning() {
        CommerceAuctionSessionDO session = runningSession().setStatus(10).setStartingPrice(1000L).setMinIncrement(100L);
        when(sessionMapper.selectByIdForUpdate(10L)).thenReturn(session);
        when(bidMapper.selectBySessionAndKey(10L, "bid-0001")).thenReturn(null);
        when(bidMapper.selectHighest(10L)).thenReturn(null);
        when(bidMapper.insert(any(CommerceAuctionBidDO.class))).thenAnswer(invocation -> { invocation.<CommerceAuctionBidDO>getArgument(0).setId(20L); return 1; });

        assertEquals(20L, service.bid(7L, new AuctionBidReqVO().setSessionId(10L).setAmount(1000L).setIdempotencyKey("bid-0001")));
        verify(bidMapper).insert(any(CommerceAuctionBidDO.class));
        verify(sessionMapper).update(any(), any());
    }

    @Test
    void bid_rejectsBelowMinimumIncrement() {
        when(sessionMapper.selectByIdForUpdate(10L)).thenReturn(runningSession().setStatus(20).setStartingPrice(1000L).setMinIncrement(100L));
        when(bidMapper.selectBySessionAndKey(10L, "bid-0001")).thenReturn(null);
        when(bidMapper.selectHighest(10L)).thenReturn(new CommerceAuctionBidDO().setAmount(1200L));

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.bid(7L, new AuctionBidReqVO().setSessionId(10L).setAmount(1250L).setIdempotencyKey("bid-0001")));
        assertEquals(AUCTION_BID_INVALID.getCode(), error.getCode());
        verify(bidMapper, never()).insert(any(CommerceAuctionBidDO.class));
    }

    @Test
    void markUnpaidSettlementsFailed_marksCanceledWinnerOrderAndIsIdempotent() {
        CommerceAuctionSessionDO session = runningSession().setStatus(AuctionStatusEnum.ENDED.getStatus()).setSettlementOrderId(30L);
        when(sessionMapper.selectSettledEndedForUpdate(100)).thenReturn(java.util.List.of(session));
        when(orderMapper.selectByIdForUpdate(30L)).thenReturn(new CommerceOrderDO().setStatus(OrderStatusEnum.CANCELED.getStatus()));
        when(sessionMapper.markSettlementFailed(eq(10L), any(), eq("竞拍胜者订单支付超时"))).thenReturn(1);

        assertEquals(1, service.markUnpaidSettlementsFailed(LocalDateTime.now(), 100));
        verify(sessionMapper).markSettlementFailed(eq(10L), any(), eq("竞拍胜者订单支付超时"));
    }

    private CommerceAuctionSessionDO runningSession() {
        return new CommerceAuctionSessionDO().setId(10L).setMerchantId(99L).setStartTime(LocalDateTime.now().minusMinutes(1))
                .setEndTime(LocalDateTime.now().plusMinutes(10));
    }

    private MerchantAccessContext merchantContext() {
        return new MerchantAccessContext(new MerchantDO().setId(99L), new StoreDO().setId(199L).setMerchantId(99L));
    }

    private AuctionUpdateReqVO updateRequest() {
        return (AuctionUpdateReqVO) new AuctionUpdateReqVO().setId(10L).setName(" Updated auction ")
                .setProductId(201L).setSkuId(301L).setStartingPrice(1000L).setMinIncrement(100L)
                .setStartTime(LocalDateTime.now().plusHours(1)).setEndTime(LocalDateTime.now().plusHours(2));
    }
}
