package cn.iocoder.yudao.module.commerce.service.auction;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.auction.vo.AuctionCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.auction.vo.AuctionPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionBidReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionRespVO;
import java.time.LocalDateTime;

public interface AuctionService {
    Long create(AuctionCreateReqVO reqVO);
    PageResult<AuctionRespVO> getOwnPage(AuctionPageReqVO reqVO);
    AuctionRespVO get(Long id, boolean publicOnly);
    void publish(Long id);
    void cancel(Long id);
    void end(Long id);
    Long bid(Long userId, AuctionBidReqVO reqVO);
    Long settle(Long userId, Long sessionId, Long addressId);
    PageResult<AuctionRespVO> getPublicPage(cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionPageReqVO reqVO);
    int advanceStatuses(LocalDateTime now, int batchSize);
    int markUnpaidSettlementsFailed(LocalDateTime now, int batchSize);
}
