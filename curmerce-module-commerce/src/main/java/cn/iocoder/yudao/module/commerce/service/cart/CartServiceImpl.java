package cn.iocoder.yudao.module.commerce.service.cart;

import cn.iocoder.yudao.module.commerce.controller.app.cart.vo.*;
import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.*;
import cn.iocoder.yudao.module.commerce.dal.dataobject.cart.CartItemDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.cart.CartItemMapper;
import cn.iocoder.yudao.module.commerce.enums.CartInvalidReasonEnum;
import cn.iocoder.yudao.module.commerce.service.catalog.PublicCatalogService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class CartServiceImpl implements CartService {
    @Resource private CartItemMapper cartMapper;
    @Resource private PublicCatalogService catalogService;
    @Resource private MemberUserApi memberUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(Long userId, CartAddReqVO reqVO) {
        memberUserApi.validateActiveUser(userId);
        ProductSkuDO sku = requireVisibleSku(reqVO.getSkuId());
        validateQuantity(reqVO.getQuantity(), sku.getStock());
        CartItemDO item = new CartItemDO().setMemberUserId(userId).setProductId(sku.getProductId()).setSkuId(sku.getId())
                .setQuantity(reqVO.getQuantity()).setSelected(true);
        try {
            cartMapper.insert(item);
            return item.getId();
        } catch (DuplicateKeyException duplicate) {
            CartItemDO existing = cartMapper.selectByUserAndSkuForUpdate(userId, sku.getId());
            if (existing == null) throw duplicate;
            int quantity = existing.getQuantity() + reqVO.getQuantity();
            validateQuantity(quantity, sku.getStock());
            if (cartMapper.updateQuantity(existing.getId(), userId, quantity) != 1) throw exception(CART_ITEM_NOT_EXISTS);
            return existing.getId();
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, CartQuantityUpdateReqVO reqVO) {
        memberUserApi.validateActiveUser(userId);
        CartItemDO item = requireOwned(userId, reqVO.getId());
        ProductSkuDO sku = requireVisibleSku(item.getSkuId());
        validateQuantity(reqVO.getQuantity(), sku.getStock());
        if (cartMapper.updateQuantity(item.getId(), userId, reqVO.getQuantity()) != 1) throw exception(CART_ITEM_NOT_EXISTS);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void updateSelected(Long userId, CartSelectionUpdateReqVO reqVO) {
        memberUserApi.validateActiveUser(userId);
        cartMapper.updateSelected(reqVO.getIds(), userId, reqVO.getSelected());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, CartBatchReqVO reqVO) {
        memberUserApi.validateActiveUser(userId);
        cartMapper.deleteOwned(userId, reqVO.getIds());
    }

    @Override @Transactional(readOnly = true)
    public CartListRespVO list(Long userId) {
        memberUserApi.validateActiveUser(userId);
        List<CartItemRespVO> valid = new ArrayList<>(), invalid = new ArrayList<>();
        for (CartItemDO item : cartMapper.selectListByUserId(userId)) {
            CartItemRespVO response = toResponse(item);
            if (response.getInvalidReason() == null) valid.add(response); else invalid.add(response);
        }
        return new CartListRespVO().setValidList(valid).setInvalidList(invalid);
    }

    @Override @Transactional(readOnly = true)
    public Long count(Long userId) { memberUserApi.validateActiveUser(userId); return cartMapper.sumQuantity(userId); }

    private ProductSkuDO requireVisibleSku(Long skuId) {
        ProductSkuDO sku = catalogService.getVisibleSku(skuId);
        if (sku == null) throw exception(CART_SKU_NOT_AVAILABLE);
        return sku;
    }
    private CartItemDO requireOwned(Long userId, Long id) {
        CartItemDO item = cartMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartItemDO>()
                .eq(CartItemDO::getId, id).eq(CartItemDO::getMemberUserId, userId));
        if (item == null) throw exception(CART_ITEM_NOT_EXISTS);
        return item;
    }
    private void validateQuantity(int quantity, Integer stock) {
        if (quantity < 1 || quantity > 99 || stock == null || quantity > stock) throw exception(CART_QUANTITY_INVALID);
    }
    private CartItemRespVO toResponse(CartItemDO item) {
        CartItemRespVO response = new CartItemRespVO(); response.setId(item.getId()); response.setQuantity(item.getQuantity()); response.setSelected(item.getSelected());
        PublicProductSummaryRespVO product = catalogService.getVisibleSummary(item.getProductId(), null);
        ProductSkuDO sku = catalogService.getVisibleSku(item.getSkuId());
        response.setProduct(product);
        if (sku != null) {
            PublicProductDetailRespVO detail = catalogService.getProductDetail(item.getProductId());
            PublicProductSkuRespVO skuResponse = detail.getSkus().stream().filter(s -> Objects.equals(s.getId(), sku.getId())).findFirst().orElse(null);
            response.setSku(skuResponse);
            if (product == null) response.setInvalidReason(CartInvalidReasonEnum.PRODUCT_UNAVAILABLE.name());
            else if (skuResponse == null) response.setInvalidReason(CartInvalidReasonEnum.SKU_UNAVAILABLE.name());
            else if (item.getQuantity() > Optional.ofNullable(sku.getStock()).orElse(0)) response.setInvalidReason(CartInvalidReasonEnum.INSUFFICIENT_STOCK.name());
        } else {
            response.setInvalidReason(product == null ? CartInvalidReasonEnum.PRODUCT_UNAVAILABLE.name() : CartInvalidReasonEnum.SKU_UNAVAILABLE.name());
        }
        return response;
    }
}
