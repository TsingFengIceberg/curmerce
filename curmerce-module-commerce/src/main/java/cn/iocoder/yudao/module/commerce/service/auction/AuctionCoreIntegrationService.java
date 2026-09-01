package cn.iocoder.yudao.module.commerce.service.auction;

import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionItemCheckReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionItemCheckRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionOrderReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionOrderRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreMerchantOwnerRespDTO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantOperatorMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantOperatorTypeEnum;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/** Core-owned validation and settlement adapter used by the independent Auction service. */
@Service
public class AuctionCoreIntegrationService {
    @Resource private MerchantOperatorMapper operatorMapper;
    @Resource private MerchantMapper merchantMapper;
    @Resource private StoreMapper storeMapper;
    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private OrderService orderService;

    @Transactional(readOnly = true)
    public CoreAuctionItemCheckRespDTO validateOwnedItem(CoreAuctionItemCheckReqDTO request) {
        MerchantOperatorDO owner = operatorMapper.selectListByUserId(request.getUserId()).stream()
                .filter(item -> MerchantOperatorTypeEnum.OWNER.getType().equals(item.getOperatorType()))
                .filter(item -> item.getStatus() != null && item.getStatus() == 0)
                .findFirst().orElse(null);
        if (owner == null) return null;
        MerchantDO merchant = merchantMapper.selectById(owner.getMerchantId());
        StoreDO store = merchant == null ? null : storeMapper.selectByMerchantId(merchant.getId());
        ProductDO product = merchant == null ? null : productMapper.selectByIdAndMerchantId(request.getProductId(), merchant.getId());
        ProductSkuDO sku = product == null ? null : skuMapper.selectByIdAndProductIdForUpdate(request.getSkuId(), request.getProductId());
        if (merchant == null || !MerchantAuditStatusEnum.APPROVED.getStatus().equals(merchant.getStatus())
                || store == null || product == null || !store.getId().equals(product.getStoreId())
                || sku == null || !merchant.getId().equals(sku.getMerchantId()) || sku.getStock() == null || sku.getStock() < 1) {
            return null;
        }
        String skuLabel = sku.getSpecificationValues() == null || sku.getSpecificationValues().isEmpty()
                ? sku.getCode() : sku.getSpecificationValues().stream()
                .map(value -> value.getName() + ": " + value.getValue())
                .reduce((left, right) -> left + " / " + right).orElse(sku.getCode());
        return new CoreAuctionItemCheckRespDTO().setMerchantId(merchant.getId()).setStoreId(store.getId())
                .setProductId(product.getId()).setSkuId(sku.getId()).setProductName(product.getName())
                .setProductImageUrl(sku.getImageUrl() == null ? product.getMainImageUrl() : sku.getImageUrl())
                .setSkuLabel(skuLabel).setSkuPrice(sku.getPrice()).setSkuStock(sku.getStock());
    }

    @Transactional(rollbackFor = Exception.class)
    public CoreAuctionOrderRespDTO createSettlementOrder(CoreAuctionOrderReqDTO request) {
        var response = orderService.createAuctionOrder(request.getUserId(), request.getAddressId(), request.getProductId(),
                request.getSkuId(), request.getAmount(), "auction-" + request.getSessionId());
        return new CoreAuctionOrderRespDTO().setOrderId(response.getOrderId()).setOrderNo(response.getOrderNo())
                .setStatus(response.getStatus());
    }

    @Transactional(readOnly = true)
    public CoreMerchantOwnerRespDTO findMerchantOwner(Long userId) {
        MerchantOperatorDO owner = operatorMapper.selectListByUserId(userId).stream()
                .filter(item -> MerchantOperatorTypeEnum.OWNER.getType().equals(item.getOperatorType()))
                .filter(item -> item.getStatus() != null && item.getStatus() == 0)
                .findFirst().orElse(null);
        if (owner == null) return null;
        MerchantDO merchant = merchantMapper.selectById(owner.getMerchantId());
        StoreDO store = merchant == null ? null : storeMapper.selectByMerchantId(merchant.getId());
        if (merchant == null || !MerchantAuditStatusEnum.APPROVED.getStatus().equals(merchant.getStatus()) || store == null) return null;
        return new CoreMerchantOwnerRespDTO().setMerchantId(merchant.getId()).setStoreId(store.getId());
    }
}
