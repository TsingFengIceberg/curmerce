package cn.iocoder.yudao.module.commerce.service.auction;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.auction.vo.AuctionCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.auction.vo.AuctionPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionBidReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionBidDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionSessionDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.auction.CommerceAuctionBidMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.auction.CommerceAuctionSessionMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.auction.AuctionStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class AuctionServiceImpl implements AuctionService {
    @Resource private CommerceAuctionSessionMapper sessionMapper;
    @Resource private CommerceAuctionBidMapper bidMapper;
    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private MerchantAccessService merchantAccessService;
    @Resource private MemberUserApi memberUserApi;
    @Resource private OrderService orderService;
    @Resource private CommerceOrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AuctionCreateReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        if (!reqVO.getEndTime().isAfter(reqVO.getStartTime())) throw exception(AUCTION_TIME_INVALID);
        ProductDO product = productMapper.selectByIdAndMerchantId(reqVO.getProductId(), context.merchant().getId());
        ProductSkuDO sku = product == null ? null : skuMapper.selectByIdAndProductIdForUpdate(reqVO.getSkuId(), reqVO.getProductId());
        if (product == null || sku == null || !context.store().getId().equals(product.getStoreId())
                || !context.merchant().getId().equals(sku.getMerchantId()) || sku.getStock() == null || sku.getStock() < 1) {
            throw exception(AUCTION_ITEM_INVALID);
        }
        CommerceAuctionSessionDO session = new CommerceAuctionSessionDO().setMerchantId(context.merchant().getId())
                .setStoreId(context.store().getId()).setProductId(product.getId()).setSkuId(sku.getId())
                .setName(reqVO.getName().trim()).setStatus(AuctionStatusEnum.DRAFT.getStatus())
                .setStartingPrice(reqVO.getStartingPrice()).setMinIncrement(reqVO.getMinIncrement())
                .setStartTime(reqVO.getStartTime()).setEndTime(reqVO.getEndTime());
        sessionMapper.insert(session);
        return session.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuctionRespVO> getOwnPage(AuctionPageReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        return mapPage(sessionMapper.selectOwnPage(reqVO, context.merchant().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuctionRespVO> getPublicPage(cn.iocoder.yudao.module.commerce.controller.app.auction.vo.AuctionPageReqVO reqVO) {
        return mapPage(sessionMapper.selectPublicPage(reqVO));
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionRespVO get(Long id, boolean publicOnly) {
        CommerceAuctionSessionDO session = sessionMapper.selectById(id);
        if (session == null || (publicOnly && (session.getStatus() == null
                || (session.getStatus() != AuctionStatusEnum.SCHEDULED.getStatus()
                && session.getStatus() != AuctionStatusEnum.RUNNING.getStatus())))) throw exception(AUCTION_NOT_FOUND);
        return toResponse(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        CommerceAuctionSessionDO session = requireOwned(id, context);
        if (!AuctionStatusEnum.DRAFT.getStatus().equals(session.getStatus())) throw exception(AUCTION_STATE_INVALID);
        if (!session.getEndTime().isAfter(session.getStartTime())) throw exception(AUCTION_TIME_INVALID);
        if (sessionMapper.updateStatus(id, context.merchant().getId(), AuctionStatusEnum.DRAFT.getStatus(), AuctionStatusEnum.SCHEDULED.getStatus()) != 1) throw exception(AUCTION_STATE_INVALID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        CommerceAuctionSessionDO session = requireOwned(id, context);
        if (!AuctionStatusEnum.DRAFT.getStatus().equals(session.getStatus()) && !AuctionStatusEnum.SCHEDULED.getStatus().equals(session.getStatus())) throw exception(AUCTION_STATE_INVALID);
        if (sessionMapper.updateStatus(id, context.merchant().getId(), session.getStatus(), AuctionStatusEnum.CANCELED.getStatus()) != 1) throw exception(AUCTION_STATE_INVALID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void end(Long id) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        CommerceAuctionSessionDO session = requireOwnedForUpdate(id, context);
        LocalDateTime now = LocalDateTime.now();
        if (!AuctionStatusEnum.SCHEDULED.getStatus().equals(session.getStatus()) && !AuctionStatusEnum.RUNNING.getStatus().equals(session.getStatus())) throw exception(AUCTION_STATE_INVALID);
        if (now.isBefore(session.getEndTime())) throw exception(AUCTION_STATE_INVALID);
        CommerceAuctionBidDO highest = bidMapper.selectHighest(id);
        session.setStatus(AuctionStatusEnum.ENDED.getStatus());
        if (highest != null) session.setWinnerUserId(highest.getBidderUserId()).setWinningBidId(highest.getId());
        sessionMapper.updateById(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long bid(Long userId, AuctionBidReqVO reqVO) {
        memberUserApi.validateActiveUserForUpdate(userId);
        CommerceAuctionSessionDO session = sessionMapper.selectByIdForUpdate(reqVO.getSessionId());
        if (session == null) throw exception(AUCTION_NOT_FOUND);
        if (session.getMerchantId() != null && session.getMerchantId().equals(userId)) throw exception(AUCTION_BID_INVALID);
        LocalDateTime now = LocalDateTime.now();
        if (!AuctionStatusEnum.SCHEDULED.getStatus().equals(session.getStatus()) && !AuctionStatusEnum.RUNNING.getStatus().equals(session.getStatus())
                || now.isBefore(session.getStartTime()) || !now.isBefore(session.getEndTime())) throw exception(AUCTION_STATE_INVALID);
        CommerceAuctionBidDO existing = bidMapper.selectBySessionAndKey(session.getId(), reqVO.getIdempotencyKey().trim());
        if (existing != null) return existing.getId();
        CommerceAuctionBidDO highest = bidMapper.selectHighest(session.getId());
        long minimum = highest == null ? session.getStartingPrice() : highest.getAmount() + session.getMinIncrement();
        if (reqVO.getAmount() < minimum) throw exception(AUCTION_BID_INVALID);
        CommerceAuctionBidDO bid = new CommerceAuctionBidDO().setSessionId(session.getId()).setBidderUserId(userId)
                .setAmount(reqVO.getAmount()).setIdempotencyKey(reqVO.getIdempotencyKey().trim());
        try { bidMapper.insert(bid); } catch (DuplicateKeyException ex) { throw exception(AUCTION_BID_DUPLICATE); }
        if (AuctionStatusEnum.SCHEDULED.getStatus().equals(session.getStatus())) {
            sessionMapper.update(new CommerceAuctionSessionDO().setStatus(AuctionStatusEnum.RUNNING.getStatus()),
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CommerceAuctionSessionDO>()
                            .eq(CommerceAuctionSessionDO::getId, session.getId()).eq(CommerceAuctionSessionDO::getStatus, AuctionStatusEnum.SCHEDULED.getStatus()));
        }
        return bid.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long settle(Long userId, Long sessionId, Long addressId) {
        memberUserApi.validateActiveUserForUpdate(userId);
        CommerceAuctionSessionDO session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null) throw exception(AUCTION_NOT_FOUND);
        if (!AuctionStatusEnum.ENDED.getStatus().equals(session.getStatus()) || !userId.equals(session.getWinnerUserId())) throw exception(AUCTION_STATE_INVALID);
        if (session.getSettlementOrderId() != null) return session.getSettlementOrderId();
        CommerceAuctionBidDO bid = bidMapper.selectById(session.getWinningBidId());
        if (bid == null) throw exception(AUCTION_WINNER_NOT_FOUND);
        Long orderId = orderService.createAuctionOrder(userId, addressId, session.getProductId(), session.getSkuId(), bid.getAmount(), "auction-" + sessionId).getOrderId();
        sessionMapper.update(new CommerceAuctionSessionDO().setSettlementOrderId(orderId),
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CommerceAuctionSessionDO>()
                        .eq(CommerceAuctionSessionDO::getId, sessionId).isNull(CommerceAuctionSessionDO::getSettlementOrderId));
        return orderId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int advanceStatuses(LocalDateTime now, int batchSize) {
        int changed = sessionMapper.promoteScheduled(now);
        for (CommerceAuctionSessionDO session : sessionMapper.selectExpiredForUpdate(now, batchSize)) {
            CommerceAuctionBidDO highest = bidMapper.selectHighest(session.getId());
            session.setStatus(AuctionStatusEnum.ENDED.getStatus());
            if (highest != null) session.setWinnerUserId(highest.getBidderUserId()).setWinningBidId(highest.getId());
            if (sessionMapper.updateById(session) == 1) changed++;
        }
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markUnpaidSettlementsFailed(LocalDateTime now, int batchSize) {
        int changed = 0;
        for (CommerceAuctionSessionDO session : sessionMapper.selectSettledEndedForUpdate(batchSize)) {
            var order = orderMapper.selectByIdForUpdate(session.getSettlementOrderId());
            if (order == null || !OrderStatusEnum.CANCELED.getStatus().equals(order.getStatus())) continue;
            if (sessionMapper.markSettlementFailed(session.getId(), now, "竞拍胜者订单支付超时") == 1) changed++;
        }
        return changed;
    }

    private CommerceAuctionSessionDO requireOwned(Long id, MerchantAccessContext context) {
        CommerceAuctionSessionDO session = sessionMapper.selectById(id);
        if (session == null || !context.merchant().getId().equals(session.getMerchantId()) || !context.store().getId().equals(session.getStoreId())) throw exception(AUCTION_NOT_FOUND);
        return session;
    }
    private CommerceAuctionSessionDO requireOwnedForUpdate(Long id, MerchantAccessContext context) {
        CommerceAuctionSessionDO session = sessionMapper.selectByIdForUpdate(id);
        if (session == null || !context.merchant().getId().equals(session.getMerchantId()) || !context.store().getId().equals(session.getStoreId())) throw exception(AUCTION_NOT_FOUND);
        return session;
    }
    private PageResult<AuctionRespVO> mapPage(PageResult<CommerceAuctionSessionDO> page) {
        return new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal());
    }
    private AuctionRespVO toResponse(CommerceAuctionSessionDO session) {
        CommerceAuctionBidDO highest = bidMapper.selectHighest(session.getId());
        return new AuctionRespVO().setId(session.getId()).setName(session.getName()).setProductId(session.getProductId()).setSkuId(session.getSkuId())
                .setStatus(session.getStatus()).setStartingPrice(session.getStartingPrice()).setMinIncrement(session.getMinIncrement())
                .setStartTime(session.getStartTime()).setEndTime(session.getEndTime()).setCurrentAmount(highest == null ? null : highest.getAmount())
                .setCurrentBidderUserId(highest == null ? null : highest.getBidderUserId()).setWinnerUserId(session.getWinnerUserId())
                .setWinningBidId(session.getWinningBidId()).setSettlementFailedTime(session.getSettlementFailedTime())
                .setSettlementFailureReason(session.getSettlementFailureReason());
    }
}
