package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleaseCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleasePageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleaseUpdateReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleaseRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseCampaignDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseItemDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseCampaignMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseItemMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleasePurchaseMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseReservationMapper;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.order.CommerceOrderMapper;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.enums.release.ReleaseStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.release.ReleasePurchaseStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleaseRespVO.Item;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class ReleaseServiceImpl implements ReleaseService {
    @Resource private CommerceReleaseCampaignMapper campaignMapper;
    @Resource private CommerceReleaseItemMapper itemMapper;
    @Resource private CommerceReleasePurchaseMapper purchaseMapper;
    @Resource private CommerceReleaseReservationMapper reservationMapper;
    @Resource private CommerceOrderMapper orderMapper;
    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private MerchantAccessService merchantAccessService;
    @Resource private MemberUserApi memberUserApi;
    @Resource private OrderService orderService;
    @Autowired(required = false) private ReleaseReservationService reservationService;
    @Autowired(required = false) private ReleaseTrafficGate trafficGate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReleaseCreateReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        validateInput(reqVO, context);
        CommerceReleaseCampaignDO campaign = new CommerceReleaseCampaignDO()
                .setMerchantId(context.merchant().getId()).setStoreId(context.store().getId())
                .setName(reqVO.getName().trim()).setStatus(ReleaseStatusEnum.DRAFT.getStatus())
                .setStartTime(reqVO.getStartTime()).setEndTime(reqVO.getEndTime())
                .setPerUserLimit(reqVO.getPerUserLimit());
        campaignMapper.insert(campaign);
        for (ReleaseCreateReqVO.Item item : reqVO.getItems()) {
            itemMapper.insert(new CommerceReleaseItemDO().setCampaignId(campaign.getId()).setProductId(item.getProductId())
                    .setSkuId(item.getSkuId()).setCampaignPrice(item.getCampaignPrice())
                    .setStock(item.getStock()).setSoldCount(0));
        }
        return campaign.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ReleaseUpdateReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        CommerceReleaseCampaignDO campaign = requireOwnedForUpdate(reqVO.getId(), context);
        if (!ReleaseStatusEnum.DRAFT.getStatus().equals(campaign.getStatus())) throw exception(RELEASE_STATE_INVALID);
        validateInput(reqVO, context);
        campaignMapper.updateById(new CommerceReleaseCampaignDO().setId(campaign.getId())
                .setName(reqVO.getName().trim()).setStartTime(reqVO.getStartTime())
                .setEndTime(reqVO.getEndTime()).setPerUserLimit(reqVO.getPerUserLimit()));
        itemMapper.deleteByCampaignId(campaign.getId());
        for (ReleaseCreateReqVO.Item item : reqVO.getItems()) {
            itemMapper.insert(new CommerceReleaseItemDO().setCampaignId(campaign.getId()).setProductId(item.getProductId())
                    .setSkuId(item.getSkuId()).setCampaignPrice(item.getCampaignPrice())
                    .setStock(item.getStock()).setSoldCount(0));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ReleaseRespVO> getOwnPage(ReleasePageReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        return mapPage(campaignMapper.selectOwnPage(reqVO, context.merchant().getId()), false);
    }

    @Override
    @Transactional(readOnly = true)
    public ReleaseRespVO getOwn(Long id) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        return toResponse(requireOwned(id, context));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ReleaseRespVO> getPublicPage(cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePageReqVO reqVO) {
        return mapPage(campaignMapper.selectPublicPage(reqVO), true);
    }

    @Override
    @Transactional(readOnly = true)
    public ReleaseRespVO get(Long id, boolean publicOnly) {
        CommerceReleaseCampaignDO campaign = campaignMapper.selectById(id);
        if (campaign == null || (publicOnly && (campaign.getStatus() == null
                || (campaign.getStatus() != ReleaseStatusEnum.SCHEDULED.getStatus()
                && campaign.getStatus() != ReleaseStatusEnum.RUNNING.getStatus())))) {
            throw exception(RELEASE_NOT_FOUND);
        }
        return toResponse(campaign);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        CommerceReleaseCampaignDO campaign = requireOwned(id, context);
        if (!ReleaseStatusEnum.DRAFT.getStatus().equals(campaign.getStatus())) throw exception(RELEASE_STATE_INVALID);
        if (!campaign.getEndTime().isAfter(campaign.getStartTime())) throw exception(RELEASE_TIME_INVALID);
        if (campaignMapper.updateStatus(id, context.merchant().getId(), ReleaseStatusEnum.DRAFT.getStatus(), ReleaseStatusEnum.SCHEDULED.getStatus()) != 1) {
            throw exception(RELEASE_STATE_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        CommerceReleaseCampaignDO campaign = requireOwned(id, context);
        if (!ReleaseStatusEnum.DRAFT.getStatus().equals(campaign.getStatus())
                && !ReleaseStatusEnum.SCHEDULED.getStatus().equals(campaign.getStatus())) throw exception(RELEASE_STATE_INVALID);
        if (campaignMapper.updateStatus(id, context.merchant().getId(), campaign.getStatus(), ReleaseStatusEnum.CANCELED.getStatus()) != 1) throw exception(RELEASE_STATE_INVALID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        CommerceReleaseCampaignDO campaign = requireOwned(id, context);
        if (!ReleaseStatusEnum.SCHEDULED.getStatus().equals(campaign.getStatus())
                && !ReleaseStatusEnum.RUNNING.getStatus().equals(campaign.getStatus())) throw exception(RELEASE_STATE_INVALID);
        if (campaignMapper.updateStatus(id, context.merchant().getId(), campaign.getStatus(), ReleaseStatusEnum.ENDED.getStatus()) != 1) throw exception(RELEASE_STATE_INVALID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleasePurchaseRespVO purchase(Long userId, ReleasePurchaseReqVO reqVO) {
        memberUserApi.validateActiveUserForUpdate(userId);
        String idempotencyKey = reqVO.getIdempotencyKey() == null ? "" : reqVO.getIdempotencyKey().trim();
        String reservationKey = reservationService == null ? null : reservationService.reservationKey(idempotencyKey);
        CommerceReleaseItemDO snapshot = itemMapper.selectById(reqVO.getItemId());
        if (snapshot == null) snapshot = itemMapper.selectByIdForUpdate(reqVO.getItemId());
        CommerceReleaseCampaignDO campaignSnapshot = snapshot == null ? null : campaignMapper.selectById(snapshot.getCampaignId());
        if (campaignSnapshot == null && snapshot != null) campaignSnapshot = campaignMapper.selectByIdForUpdate(snapshot.getCampaignId());
        LocalDateTime now = LocalDateTime.now();
        if (!isOpen(campaignSnapshot, now) || snapshot == null) throw exception(RELEASE_STATE_INVALID);
        // Reject duplicate buyers before touching Redis or acquiring the item
        // lock. Besides avoiding needless reservations, this preserves the
        // duplicate-purchase contract even when a stale snapshot has no stock.
        CommerceReleasePurchaseDO existingPurchase = purchaseMapper.selectByBuyerAndItem(userId, snapshot.getId());
        if (existingPurchase != null) {
            CommerceOrderDO existingOrder = orderMapper.selectById(existingPurchase.getOrderId());
            // A Redis Stream redelivery can happen after the database commit
            // but before XACK.  Treat the same request key as a replay and
            // return the committed result instead of creating a second order.
            if (existingOrder != null && idempotencyKey.equals(existingOrder.getIdempotencyKey())) {
                return purchaseResponse(existingPurchase, existingOrder);
            }
            throw exception(RELEASE_PURCHASE_DUPLICATE);
        }
        if (trafficGate != null) {
            ReleaseTrafficGate.Result traffic = trafficGate.allow(campaignSnapshot.getId(), userId);
            if (traffic == ReleaseTrafficGate.Result.LIMITED || traffic == ReleaseTrafficGate.Result.UNAVAILABLE) {
                throw exception(RELEASE_RESERVATION_UNAVAILABLE);
            }
        }
        ReleaseReservationService.ReservationResult reservation = reserveInventory(campaignSnapshot, snapshot, userId,
                reqVO.getQuantity(), reservationKey);
        boolean completed = false;
        try {
            CommerceReleaseItemDO item = itemMapper.selectByIdForUpdate(reqVO.getItemId());
            CommerceReleaseCampaignDO campaign = item == null ? null : campaignMapper.selectByIdForUpdate(item.getCampaignId());
            if (!isOpen(campaign, now) || item == null) throw exception(RELEASE_STATE_INVALID);
            CommerceReleasePurchaseDO existing = purchaseMapper.selectByBuyerAndItem(userId, item.getId());
            if (existing != null) {
                // Another request with this key can commit while this request
                // waits for the item row lock. Re-read its order and preserve
                // idempotency instead of reporting a false duplicate.
                CommerceOrderDO existingOrder = orderMapper.selectById(existing.getOrderId());
                if (existingOrder != null && idempotencyKey.equals(existingOrder.getIdempotencyKey())) {
                    return purchaseResponse(existing, existingOrder);
                }
                throw exception(RELEASE_PURCHASE_DUPLICATE);
            }
            if (reqVO.getQuantity() > campaign.getPerUserLimit() || reqVO.getQuantity() > item.getStock()) {
                throw exception(reqVO.getQuantity() > item.getStock() ? RELEASE_STOCK_INSUFFICIENT : RELEASE_PURCHASE_LIMIT);
            }
            if (itemMapper.updateInventory(item.getId(), reqVO.getQuantity()) != 1) throw exception(RELEASE_STOCK_INSUFFICIENT);
            cn.iocoder.yudao.module.commerce.controller.app.order.vo.OrderCreateRespVO order = orderService.createReleaseOrder(
                    userId, reqVO.getAddressId(), item.getProductId(), item.getSkuId(), item.getCampaignPrice(),
                    reqVO.getQuantity(), idempotencyKey);
            CommerceReleasePurchaseDO purchase = new CommerceReleasePurchaseDO().setCampaignId(campaign.getId()).setItemId(item.getId())
                    .setBuyerUserId(userId).setOrderId(order.getOrderId()).setQuantity(reqVO.getQuantity())
                    .setUnitPrice(item.getCampaignPrice()).setStatus(ReleasePurchaseStatusEnum.PENDING.getStatus());
            try { purchaseMapper.insert(purchase); } catch (DuplicateKeyException ex) { throw exception(RELEASE_PURCHASE_DUPLICATE); }
            completed = true;
            if (isReservationPresent(reservation) && reservationMapper != null) {
                reservationMapper.insert(new CommerceReleaseReservationDO()
                        .setTenantId(tenantId()).setReservationKey(reservationKey).setCampaignId(campaign.getId()).setItemId(item.getId())
                        .setBuyerUserId(userId).setPurchaseId(purchase.getId()).setQuantity(reqVO.getQuantity())
                        .setStatus(CommerceReleaseReservationDO.COMMITTED));
            }
            registerReservationCompletion(reservation, campaign.getId(), item.getId(), userId,
                    reqVO.getQuantity(), reservationKey);
            return new ReleasePurchaseRespVO().setPurchaseId(purchase.getId()).setCampaignId(campaign.getId())
                    .setItemId(item.getId()).setQuantity(purchase.getQuantity()).setUnitPrice(purchase.getUnitPrice())
                    .setOrderId(order.getOrderId()).setOrderNo(order.getOrderNo()).setOrderStatus(order.getStatus());
        } catch (RuntimeException ex) {
            if (isOwnedReservation(reservation) && !completed && reservationService != null) {
                // The transaction synchronization below handles the normal
                // rollback path.  Direct calls without an active transaction
                // still need a best-effort release here.
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    reservationService.releaseTracked(campaignSnapshot.getId(), snapshot.getId(), userId,
                            reqVO.getQuantity(), reservationKey);
                }
            }
            throw ex;
        }
    }

    private static String tenantId() {
        Long value = TenantContextHolder.getTenantId();
        return value == null ? "default" : String.valueOf(value);
    }

    private ReleasePurchaseRespVO purchaseResponse(CommerceReleasePurchaseDO purchase, CommerceOrderDO order) {
        return new ReleasePurchaseRespVO().setPurchaseId(purchase.getId()).setCampaignId(purchase.getCampaignId())
                .setItemId(purchase.getItemId()).setQuantity(purchase.getQuantity()).setUnitPrice(purchase.getUnitPrice())
                .setOrderId(order.getId()).setOrderNo(order.getOrderNo()).setOrderStatus(order.getStatus());
    }

    private ReleaseReservationService.ReservationResult reserveInventory(CommerceReleaseCampaignDO campaign,
                                                                           CommerceReleaseItemDO item,
                                                                           Long userId, int quantity, String reservationKey) {
        if (quantity <= 0 || quantity > campaign.getPerUserLimit()) throw exception(RELEASE_PURCHASE_LIMIT);
        int databaseStock = item.getStock() == null ? 0 : item.getStock();
        if (quantity > databaseStock) throw exception(RELEASE_STOCK_INSUFFICIENT);
        if (reservationService == null) return ReleaseReservationService.ReservationResult.DISABLED;
        ReleaseReservationService.ReservationResult result = reservationService.reserveTracked(campaign.getId(), item.getId(), userId,
                quantity, campaign.getPerUserLimit(), databaseStock, reservationKey);
        if (result == ReleaseReservationService.ReservationResult.STOCK_INSUFFICIENT) throw exception(RELEASE_STOCK_INSUFFICIENT);
        if (result == ReleaseReservationService.ReservationResult.LIMIT_EXCEEDED) throw exception(RELEASE_PURCHASE_LIMIT);
        if (result == ReleaseReservationService.ReservationResult.DUPLICATE) throw exception(RELEASE_PURCHASE_DUPLICATE);
        if (result == ReleaseReservationService.ReservationResult.IDEMPOTENCY_CONFLICT) throw exception(RELEASE_IDEMPOTENCY_CONFLICT);
        if (result == ReleaseReservationService.ReservationResult.UNAVAILABLE) throw exception(RELEASE_RESERVATION_UNAVAILABLE);
        return result;
    }

    private boolean isOwnedReservation(ReleaseReservationService.ReservationResult result) {
        // Only the Lua caller receiving RESERVED owns the lease and may
        // release it after a rollback. ALREADY_RESERVED is the same user's
        // idempotent retry: it may adopt/finalize a committed reservation, but
        // must never release a lease that another retry currently owns.
        return result == ReleaseReservationService.ReservationResult.RESERVED;
    }

    /**
     * A same-key retry may safely finish the existing Redis reservation after
     * its SQL transaction commits. This closes the crash window where the
     * first request reserved stock and died before writing the durable ledger.
     */
    private boolean isReservationPresent(ReleaseReservationService.ReservationResult result) {
        return isOwnedReservation(result)
                || result == ReleaseReservationService.ReservationResult.ALREADY_RESERVED;
    }

    /**
     * Redis is finalized only after the local SQL transaction has committed.
     * A rollback releases the gate, while a post-crash committed row remains
     * visible to ReleaseReservationReconciliationJob for retry.
     */
    private void registerReservationCompletion(ReleaseReservationService.ReservationResult result,
                                                Long campaignId, Long itemId, Long userId, int quantity,
                                                String reservationKey) {
        if (!isReservationPresent(result) || reservationService == null || reservationKey == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            AtomicBoolean finalized = new AtomicBoolean(false);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    finalized.set(reservationService.commitTracked(campaignId, itemId, userId, quantity, reservationKey));
                }
                @Override public void afterCompletion(int status) {
                    // ALREADY_RESERVED belongs to a concurrent idempotent
                    // retry. Only the invocation receiving RESERVED owns the
                    // lease and may release it after a rollback.
                    if (status != STATUS_COMMITTED && isOwnedReservation(result)) {
                        reservationService.releaseTracked(campaignId, itemId, userId, quantity, reservationKey);
                    } else if (!finalized.get() && reservationMapper != null) {
                        reservationMapper.markErrorByIdentity(campaignId, itemId, userId, reservationKey,
                                "Redis reservation finalization deferred to recovery");
                    }
                }
            });
            return;
        }
        if (!reservationService.commitTracked(campaignId, itemId, userId, quantity, reservationKey)) {
            if (isOwnedReservation(result)) {
                reservationService.releaseTracked(campaignId, itemId, userId, quantity, reservationKey);
            }
            throw exception(RELEASE_RESERVATION_UNAVAILABLE);
        }
    }


    private boolean isOpen(CommerceReleaseCampaignDO campaign, LocalDateTime now) {
        return campaign != null && (ReleaseStatusEnum.SCHEDULED.getStatus().equals(campaign.getStatus())
                || ReleaseStatusEnum.RUNNING.getStatus().equals(campaign.getStatus()))
                && !now.isBefore(campaign.getStartTime()) && now.isBefore(campaign.getEndTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int advanceStatuses(LocalDateTime now) {
        return campaignMapper.promoteScheduled(now) + campaignMapper.finishExpired(now);
    }

    private CommerceReleaseCampaignDO requireOwned(Long id, MerchantAccessContext context) {
        CommerceReleaseCampaignDO campaign = campaignMapper.selectById(id);
        if (campaign == null || !context.merchant().getId().equals(campaign.getMerchantId())
                || !context.store().getId().equals(campaign.getStoreId())) throw exception(RELEASE_NOT_FOUND);
        return campaign;
    }
    private CommerceReleaseCampaignDO requireOwnedForUpdate(Long id, MerchantAccessContext context) {
        CommerceReleaseCampaignDO campaign = campaignMapper.selectByIdForUpdate(id);
        if (campaign == null || !context.merchant().getId().equals(campaign.getMerchantId())
                || !context.store().getId().equals(campaign.getStoreId())) throw exception(RELEASE_NOT_FOUND);
        return campaign;
    }
    private void validateInput(ReleaseCreateReqVO reqVO, MerchantAccessContext context) {
        if (!reqVO.getEndTime().isAfter(reqVO.getStartTime())) throw exception(RELEASE_TIME_INVALID);
        Set<Long> skuIds = new HashSet<>();
        for (ReleaseCreateReqVO.Item item : reqVO.getItems()) {
            if (!skuIds.add(item.getSkuId())) throw exception(RELEASE_ITEM_INVALID);
            ProductDO product = productMapper.selectByIdAndMerchantId(item.getProductId(), context.merchant().getId());
            ProductSkuDO sku = product == null ? null : skuMapper.selectByIdAndProductIdForUpdate(item.getSkuId(), item.getProductId());
            if (product == null || sku == null || !context.store().getId().equals(product.getStoreId())
                    || !context.merchant().getId().equals(sku.getMerchantId()) || sku.getStock() == null
                    || item.getStock() > sku.getStock()) throw exception(RELEASE_ITEM_INVALID);
        }
    }
    private PageResult<ReleaseRespVO> mapPage(PageResult<CommerceReleaseCampaignDO> page, boolean publicOnly) {
        return new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal());
    }
    private ReleaseRespVO toResponse(CommerceReleaseCampaignDO campaign) {
        ReleaseRespVO response = new ReleaseRespVO().setId(campaign.getId()).setName(campaign.getName()).setStatus(campaign.getStatus())
                .setStartTime(campaign.getStartTime()).setEndTime(campaign.getEndTime()).setPerUserLimit(campaign.getPerUserLimit());
        response.setItems(itemMapper.selectByCampaignId(campaign.getId()).stream().map(item -> {
            ProductDO product = productMapper.selectById(item.getProductId());
            ProductSkuDO sku = skuMapper.selectById(item.getSkuId());
            String skuLabel = sku == null || sku.getSpecificationValues() == null || sku.getSpecificationValues().isEmpty()
                    ? (sku == null ? "默认规格" : sku.getCode())
                    : sku.getSpecificationValues().stream().map(value -> value.getName() + ": " + value.getValue())
                    .reduce((left, right) -> left + " / " + right).orElse("默认规格");
            return new Item().setId(item.getId()).setProductId(item.getProductId()).setSkuId(item.getSkuId())
                    .setProductName(product == null ? "商品已不可用" : product.getName())
                    .setProductImageUrl(sku != null && sku.getImageUrl() != null ? sku.getImageUrl()
                            : product == null ? null : product.getMainImageUrl())
                    .setSkuLabel(skuLabel).setOriginalPrice(sku == null ? null : sku.getPrice())
                    .setCampaignPrice(item.getCampaignPrice()).setStock(item.getStock()).setSoldCount(item.getSoldCount());
        }).toList());
        return response;
    }
}
