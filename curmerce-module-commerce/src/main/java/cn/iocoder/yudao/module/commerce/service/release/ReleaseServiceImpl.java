package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleaseCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleasePageReqVO;
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

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class ReleaseServiceImpl implements ReleaseService {
    @Resource private CommerceReleaseCampaignMapper campaignMapper;
    @Resource private CommerceReleaseItemMapper itemMapper;
    @Resource private CommerceReleasePurchaseMapper purchaseMapper;
    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private MerchantAccessService merchantAccessService;
    @Resource private MemberUserApi memberUserApi;
    @Resource private OrderService orderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReleaseCreateReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        if (!reqVO.getEndTime().isAfter(reqVO.getStartTime())) throw exception(RELEASE_TIME_INVALID);
        Set<Long> skuIds = new HashSet<>();
        for (ReleaseCreateReqVO.Item item : reqVO.getItems()) {
            if (!skuIds.add(item.getSkuId())) throw exception(RELEASE_ITEM_INVALID);
            ProductDO product = productMapper.selectByIdAndMerchantId(item.getProductId(), context.merchant().getId());
            ProductSkuDO sku = product == null ? null : skuMapper.selectByIdAndProductIdForUpdate(item.getSkuId(), item.getProductId());
            if (product == null || sku == null || !context.store().getId().equals(product.getStoreId())
                    || !context.merchant().getId().equals(sku.getMerchantId())) throw exception(RELEASE_ITEM_INVALID);
        }
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
    @Transactional(readOnly = true)
    public PageResult<ReleaseRespVO> getOwnPage(ReleasePageReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        return mapPage(campaignMapper.selectOwnPage(reqVO, context.merchant().getId()), false);
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
        CommerceReleaseItemDO item = itemMapper.selectByIdForUpdate(reqVO.getItemId());
        CommerceReleaseCampaignDO campaign = item == null ? null : campaignMapper.selectByIdForUpdate(item.getCampaignId());
        LocalDateTime now = LocalDateTime.now();
        if (item == null || campaign == null || (campaign.getStatus() != ReleaseStatusEnum.SCHEDULED.getStatus()
                && campaign.getStatus() != ReleaseStatusEnum.RUNNING.getStatus())
                || now.isBefore(campaign.getStartTime()) || !now.isBefore(campaign.getEndTime())) throw exception(RELEASE_STATE_INVALID);
        CommerceReleasePurchaseDO existing = purchaseMapper.selectByBuyerAndItem(userId, item.getId());
        if (existing != null) throw exception(RELEASE_PURCHASE_DUPLICATE);
        if (reqVO.getQuantity() > campaign.getPerUserLimit() || reqVO.getQuantity() > item.getStock()) {
            throw exception(reqVO.getQuantity() > item.getStock() ? RELEASE_STOCK_INSUFFICIENT : RELEASE_PURCHASE_LIMIT);
        }
        if (itemMapper.updateInventory(item.getId(), reqVO.getQuantity()) != 1) throw exception(RELEASE_STOCK_INSUFFICIENT);
        String idempotencyKey = reqVO.getIdempotencyKey() == null ? null : reqVO.getIdempotencyKey().trim();
        cn.iocoder.yudao.module.commerce.controller.app.order.vo.OrderCreateRespVO order = orderService.createReleaseOrder(
                userId, reqVO.getAddressId(), item.getProductId(), item.getSkuId(), item.getCampaignPrice(),
                reqVO.getQuantity(), idempotencyKey);
        CommerceReleasePurchaseDO purchase = new CommerceReleasePurchaseDO().setCampaignId(campaign.getId()).setItemId(item.getId())
                .setBuyerUserId(userId).setOrderId(order.getOrderId()).setQuantity(reqVO.getQuantity())
                .setUnitPrice(item.getCampaignPrice()).setStatus(ReleasePurchaseStatusEnum.PENDING.getStatus());
        try { purchaseMapper.insert(purchase); } catch (DuplicateKeyException ex) { throw exception(RELEASE_PURCHASE_DUPLICATE); }
        return new ReleasePurchaseRespVO().setPurchaseId(purchase.getId()).setCampaignId(campaign.getId())
                .setItemId(item.getId()).setQuantity(purchase.getQuantity()).setUnitPrice(purchase.getUnitPrice())
                .setOrderId(order.getOrderId()).setOrderNo(order.getOrderNo()).setOrderStatus(order.getStatus());
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
    private PageResult<ReleaseRespVO> mapPage(PageResult<CommerceReleaseCampaignDO> page, boolean publicOnly) {
        return new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal());
    }
    private ReleaseRespVO toResponse(CommerceReleaseCampaignDO campaign) {
        ReleaseRespVO response = new ReleaseRespVO().setId(campaign.getId()).setName(campaign.getName()).setStatus(campaign.getStatus())
                .setStartTime(campaign.getStartTime()).setEndTime(campaign.getEndTime()).setPerUserLimit(campaign.getPerUserLimit());
        response.setItems(itemMapper.selectByCampaignId(campaign.getId()).stream().map(item -> new Item().setId(item.getId())
                .setProductId(item.getProductId()).setSkuId(item.getSkuId()).setCampaignPrice(item.getCampaignPrice())
                .setStock(item.getStock()).setSoldCount(item.getSoldCount())).toList());
        return response;
    }
}
