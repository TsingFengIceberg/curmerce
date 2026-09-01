package cn.iocoder.yudao.curmerce.auction;

import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionItemCheckRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionOrderReqDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
public class AuctionOwnedService {
    private final AuctionOwnedRepository repository;
    private final AuctionCoreClient core;

    public AuctionOwnedService(AuctionOwnedRepository repository, AuctionCoreClient core) {
        this.repository = repository;
        this.core = core;
    }

    public Map<String, Object> publicPage(String keyword, int pageNo, int pageSize) {
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<AuctionOwnedRepository.AuctionSessionRow> rows = repository.page(keyword, (safePage - 1) * safeSize, safeSize, false, null);
        return page(rows.stream().map(this::toResponse).toList(), repository.count(keyword, false, null));
    }

    public Map<String, Object> ownerPage(Long merchantId, String keyword, int pageNo, int pageSize) {
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<AuctionOwnedRepository.AuctionSessionRow> rows = repository.page(keyword, (safePage - 1) * safeSize, safeSize, true, merchantId);
        return page(rows.stream().map(this::toResponse).toList(), repository.count(keyword, true, merchantId));
    }

    public Map<String, Object> get(Long id, boolean ownerOnly, Long userId) {
        AuctionOwnedRepository.AuctionSessionRow session = repository.findSession(id, false);
        if (session == null || (ownerOnly && !isOwner(userId, session))) throw new AuctionBusinessException("拍卖场次不存在");
        return toResponse(session);
    }

    public Map<String, Object> bidPage(Long sessionId, Long loginUserId, int pageNo, int pageSize) {
        AuctionOwnedRepository.AuctionSessionRow session = repository.findSession(sessionId, false);
        if (session == null || !isPublic(session.status())) throw new AuctionBusinessException("拍卖场次不存在");
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        AuctionOwnedRepository.AuctionBidRow highest = repository.highestBid(sessionId);
        List<Map<String, Object>> bids = repository.bidPage(sessionId, (safePage - 1) * safeSize, safeSize).stream().map(bid -> {
            String suffix = String.valueOf(bid.bidderUserId());
            suffix = suffix.substring(Math.max(0, suffix.length() - 4));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", bid.id()); result.put("amount", bid.amount());
            result.put("bidderLabel", loginUserId != null && loginUserId.equals(bid.bidderUserId()) ? "我" : "竞拍者 " + suffix);
            result.put("mine", loginUserId != null && loginUserId.equals(bid.bidderUserId()));
            result.put("leading", highest != null && highest.id().equals(bid.id()));
            result.put("createTime", bid.createTime());
            return result;
        }).toList();
        return page(bids, repository.bidCount(sessionId));
    }

    @Transactional(transactionManager = "auctionTransactionManager", rollbackFor = Exception.class)
    public Long create(Long userId, String name, Long productId, Long skuId, Long startingPrice, Long minIncrement,
                       LocalDateTime startTime, LocalDateTime endTime) {
        requirePermission(userId, "commerce:auction:create");
        validateTimes(startTime, endTime);
        CoreAuctionItemCheckRespDTO item = requireItem(userId, productId, skuId);
        return repository.insertSession(new AuctionOwnedRepository.AuctionCreateCommand(item.getMerchantId(), item.getStoreId(), productId, skuId,
                normalizeName(name), startingPrice, minIncrement, startTime, endTime, item.getProductName(), item.getProductImageUrl(), item.getSkuLabel(), item.getSkuPrice()));
    }

    @Transactional(transactionManager = "auctionTransactionManager", rollbackFor = Exception.class)
    public void update(Long userId, Long id, String name, Long productId, Long skuId, Long startingPrice, Long minIncrement,
                       LocalDateTime startTime, LocalDateTime endTime) {
        requirePermission(userId, "commerce:auction:update");
        validateTimes(startTime, endTime);
        AuctionOwnedRepository.AuctionSessionRow existing = requireSession(id, true);
        CoreAuctionItemCheckRespDTO item = requireItem(userId, productId, skuId);
        if (!existing.merchantId().equals(item.getMerchantId())) throw new AuctionBusinessException("无权操作该拍卖场次");
        if (repository.updateDraft(id, existing.merchantId(), new AuctionOwnedRepository.AuctionCreateCommand(item.getMerchantId(), item.getStoreId(), productId, skuId,
                normalizeName(name), startingPrice, minIncrement, startTime, endTime, item.getProductName(), item.getProductImageUrl(), item.getSkuLabel(), item.getSkuPrice())) != 1) {
            throw new AuctionBusinessException("拍卖场次状态不允许修改");
        }
    }

    @Transactional(transactionManager = "auctionTransactionManager", rollbackFor = Exception.class)
    public void publish(Long userId, Long id) { changeStatus(userId, id, 0, 10, "commerce:auction:update"); }

    @Transactional(transactionManager = "auctionTransactionManager", rollbackFor = Exception.class)
    public void cancel(Long userId, Long id) {
        requirePermission(userId, "commerce:auction:update");
        AuctionOwnedRepository.AuctionSessionRow session = requireOwned(userId, id, true);
        if (session.status() != 0 && session.status() != 10) throw new AuctionBusinessException("拍卖场次状态不允许取消");
        if (repository.updateStatus(id, session.merchantId(), session.status(), 40) != 1) throw new AuctionBusinessException("拍卖场次状态冲突");
    }

    @Transactional(transactionManager = "auctionTransactionManager", rollbackFor = Exception.class)
    public void end(Long userId, Long id) {
        requirePermission(userId, "commerce:auction:update");
        AuctionOwnedRepository.AuctionSessionRow session = requireOwned(userId, id, true);
        if ((session.status() != 10 && session.status() != 20) || LocalDateTime.now().isBefore(session.endTime())) throw new AuctionBusinessException("拍卖尚未到结束时间");
        endSession(session);
    }

    @Transactional(transactionManager = "auctionTransactionManager", rollbackFor = Exception.class)
    public Long bid(String authorization, Long sessionId, Long amount, String idempotencyKey) {
        Long userId = core.authenticate(authorization);
        AuctionOwnedRepository.AuctionSessionRow session = requireSession(sessionId, true);
        if (session.merchantId().equals(userId) || !isOpen(session)) throw new AuctionBusinessException("拍卖场次当前不可出价");
        String key = normalizeKey(idempotencyKey);
        AuctionOwnedRepository.AuctionBidRow existing = repository.findBidByKey(sessionId, key);
        if (existing != null) return existing.id();
        AuctionOwnedRepository.AuctionBidRow highest = repository.highestBid(sessionId);
        long minimum = highest == null ? session.startingPrice() : highest.amount() + session.minIncrement();
        if (amount == null || amount < minimum) throw new AuctionBusinessException("出价金额不符合规则");
        try {
            Long bidId = repository.insertBid(sessionId, userId, amount, key);
            if (session.status() == 10) repository.markRunning(sessionId);
            return bidId;
        } catch (DuplicateKeyException ex) {
            AuctionOwnedRepository.AuctionBidRow replay = repository.findBidByKey(sessionId, key);
            if (replay != null) return replay.id();
            throw new AuctionBusinessException("出价幂等键已使用");
        }
    }

    @Transactional(transactionManager = "auctionTransactionManager", rollbackFor = Exception.class)
    public Long settle(String authorization, Long sessionId, Long addressId) {
        Long userId = core.authenticate(authorization);
        AuctionOwnedRepository.AuctionSessionRow session = requireSession(sessionId, true);
        if (session.status() != 30 || !userId.equals(session.winnerUserId())) throw new AuctionBusinessException("当前用户不是拍卖胜者");
        if (session.settlementOrderId() != null) return session.settlementOrderId();
        AuctionOwnedRepository.AuctionBidRow winning = session.winningBidId() == null ? null : repository.highestBid(sessionId);
        if (winning == null) throw new AuctionBusinessException("拍卖没有有效竞拍者");
        var order = core.createSettlementOrder(new CoreAuctionOrderReqDTO().setUserId(userId).setAddressId(addressId)
                .setProductId(session.productId()).setSkuId(session.skuId()).setAmount(winning.amount()).setSessionId(sessionId));
        repository.setSettlementOrder(sessionId, order.getOrderId());
        return order.getOrderId();
    }

    @Transactional(transactionManager = "auctionTransactionManager", rollbackFor = Exception.class)
    public int advanceStatuses(LocalDateTime now, int batchSize) {
        int changed = repository.promoteScheduled(now);
        for (Long id : repository.selectExpiredIds(now, Math.min(500, Math.max(1, batchSize)))) {
            AuctionOwnedRepository.AuctionSessionRow session = repository.findSession(id, true);
            if (session != null && (session.status() == 10 || session.status() == 20)) { endSession(session); changed++; }
        }
        return changed;
    }

    private void endSession(AuctionOwnedRepository.AuctionSessionRow session) {
        AuctionOwnedRepository.AuctionBidRow highest = repository.highestBid(session.id());
        repository.markEnded(session.id(), highest == null ? null : highest.bidderUserId(), highest == null ? null : highest.id());
    }

    private void changeStatus(Long userId, Long id, int expected, int target, String permission) {
        requirePermission(userId, permission);
        AuctionOwnedRepository.AuctionSessionRow session = requireOwned(userId, id, true);
        if (repository.updateStatus(id, session.merchantId(), expected, target) != 1) throw new AuctionBusinessException("拍卖场次状态不允许该操作");
    }

    private CoreAuctionItemCheckRespDTO requireItem(Long userId, Long productId, Long skuId) {
        CoreAuctionItemCheckRespDTO item = core.checkItem(userId, productId, skuId);
        if (item == null) throw new AuctionBusinessException("拍卖商品或规格无效");
        return item;
    }

    private AuctionOwnedRepository.AuctionSessionRow requireOwned(Long userId, Long id, boolean forUpdate) {
        AuctionOwnedRepository.AuctionSessionRow session = requireSession(id, forUpdate);
        if (!isOwner(userId, session)) throw new AuctionBusinessException("无权操作该拍卖场次");
        return session;
    }

    private AuctionOwnedRepository.AuctionSessionRow requireSession(Long id, boolean forUpdate) {
        AuctionOwnedRepository.AuctionSessionRow session = repository.findSession(id, forUpdate);
        if (session == null) throw new AuctionBusinessException("拍卖场次不存在");
        return session;
    }

    private boolean isOwner(Long userId, AuctionOwnedRepository.AuctionSessionRow session) {
        CoreAuctionItemCheckRespDTO item = core.checkItem(userId, session.productId(), session.skuId());
        return item != null && session.merchantId().equals(item.getMerchantId());
    }

    private void requirePermission(Long userId, String permission) {
        if (!core.hasPermission(userId, permission)) throw new AuctionBusinessException("无权操作拍卖场次");
    }

    private static boolean isPublic(int status) { return status == 10 || status == 20 || status == 30 || status == 50; }
    private static boolean isOpen(AuctionOwnedRepository.AuctionSessionRow session) {
        LocalDateTime now = LocalDateTime.now();
        return (session.status() == 10 || session.status() == 20) && !now.isBefore(session.startTime()) && now.isBefore(session.endTime());
    }
    private static void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start) || !start.isAfter(LocalDateTime.now())) throw new AuctionBusinessException("拍卖时间无效");
    }
    private static String normalizeName(String value) { if (value == null || value.trim().isBlank() || value.trim().length() > 120) throw new AuctionBusinessException("拍卖名称无效"); return value.trim(); }
    private static String normalizeKey(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")) throw new AuctionBusinessException("幂等键无效"); return value; }
    private Map<String, Object> toResponse(AuctionOwnedRepository.AuctionSessionRow session) {
        AuctionOwnedRepository.AuctionBidRow highest = repository.highestBid(session.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", session.id()); result.put("name", session.name()); result.put("productId", session.productId()); result.put("skuId", session.skuId());
        result.put("productName", session.productName()); result.put("productImageUrl", session.productImageUrl()); result.put("skuLabel", session.skuLabel());
        result.put("originalPrice", session.originalPrice()); result.put("status", session.status()); result.put("startingPrice", session.startingPrice()); result.put("minIncrement", session.minIncrement());
        result.put("startTime", session.startTime()); result.put("endTime", session.endTime());
        result.put("currentAmount", highest == null ? null : highest.amount());
        result.put("currentBidderUserId", highest == null ? null : highest.bidderUserId());
        result.put("bidCount", repository.bidCount(session.id()));
        result.put("winnerUserId", session.winnerUserId()); result.put("winningBidId", session.winningBidId());
        result.put("settlementFailedTime", session.settlementFailedTime()); result.put("settlementFailureReason", session.settlementFailureReason());
        return result;
    }
    private static Map<String, Object> page(List<?> rows, long total) { return Map.of("list", rows, "total", total); }

    public static class AuctionBusinessException extends RuntimeException { public AuctionBusinessException(String message) { super(message); } }
}
