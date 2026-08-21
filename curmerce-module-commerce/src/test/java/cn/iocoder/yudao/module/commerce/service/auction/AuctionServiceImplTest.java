package cn.iocoder.yudao.module.commerce.service.auction;

import cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionBidReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionBidDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionSessionDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.auction.CommerceAuctionBidMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.auction.CommerceAuctionSessionMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.AUCTION_BID_INVALID;
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
    @InjectMocks private AuctionServiceImpl service;

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

    private CommerceAuctionSessionDO runningSession() {
        return new CommerceAuctionSessionDO().setId(10L).setMerchantId(99L).setStartTime(LocalDateTime.now().minusMinutes(1))
                .setEndTime(LocalDateTime.now().plusMinutes(10));
    }
}
