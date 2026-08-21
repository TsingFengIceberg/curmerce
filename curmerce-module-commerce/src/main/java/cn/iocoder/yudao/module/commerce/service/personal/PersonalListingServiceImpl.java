package cn.iocoder.yudao.module.commerce.service.personal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.*;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductCategoryMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.enums.product.ProductAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSellerTypeEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class PersonalListingServiceImpl implements PersonalListingService {
    @Resource private MemberUserApi memberUserApi;
    @Resource private ProductCategoryMapper categoryMapper;
    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper skuMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, PersonalListingCreateReqVO req) {
        memberUserApi.validateActiveUserForUpdate(userId);
        requireCategory(req.getCategoryId());
        validatePrice(req.getPrice());
        String code = "personal-" + userId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ProductDO product = new ProductDO().setSellerType(ProductSellerTypeEnum.PERSONAL.getType())
                .setSellerUserId(userId).setCategoryId(req.getCategoryId()).setCode(code)
                .setName(trim(req.getName())).setCondition(trim(req.getCondition()))
                .setMainImageUrl(trim(req.getMainImageUrl())).setImageUrls(normalizeImages(req.getImageUrls()))
                .setDescription(trim(req.getDescription())).setAuditStatus(ProductAuditStatusEnum.DRAFT.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus()).setSort(0);
        productMapper.insert(product);
        ProductSkuDO sku = new ProductSkuDO().setProductId(product.getId()).setMerchantId(null)
                .setCode(code + "-sku").setPrice(req.getPrice()).setStock(1)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0);
        skuMapper.insert(sku);
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, PersonalListingUpdateReqVO req) {
        memberUserApi.validateActiveUserForUpdate(userId);
        ProductDO current = requireForUpdate(userId, req.getId());
        requireCategory(req.getCategoryId());
        validatePrice(req.getPrice());
        if (!ProductSaleStatusEnum.OFF_SHELF.getStatus().equals(current.getSaleStatus())
                || (!ProductAuditStatusEnum.DRAFT.getStatus().equals(current.getAuditStatus())
                && !ProductAuditStatusEnum.REJECTED.getStatus().equals(current.getAuditStatus()))) {
            throw exception(PERSONAL_LISTING_STATE_INVALID);
        }
        ProductSkuDO sku = skuMapper.selectListByProductIdForUpdate(current.getId()).stream().findFirst().orElse(null);
        if (sku == null || !Integer.valueOf(1).equals(sku.getStock())) throw exception(PERSONAL_LISTING_STATE_INVALID);
        ProductDO update = new ProductDO().setId(current.getId()).setCategoryId(req.getCategoryId())
                .setName(trim(req.getName())).setCondition(trim(req.getCondition()))
                .setMainImageUrl(trim(req.getMainImageUrl())).setImageUrls(normalizeImages(req.getImageUrls()))
                .setDescription(trim(req.getDescription()));
        if (productMapper.updatePersonalFields(update, userId) != 1
                || skuMapper.updatePersonalPrice(sku.getId(), current.getId(), req.getPrice()) != 1) {
            throw exception(PERSONAL_LISTING_STATE_INVALID);
        }
    }

    @Override @Transactional(readOnly = true)
    public PersonalListingRespVO get(Long userId, Long id) {
        memberUserApi.validateActiveUser(userId);
        ProductDO product = productMapper.selectPersonalById(id, userId);
        if (product == null) throw exception(PERSONAL_LISTING_NOT_FOUND);
        return toResponse(product);
    }

    @Override @Transactional(readOnly = true)
    public PageResult<PersonalListingRespVO> page(Long userId, PersonalListingPageReqVO req) {
        memberUserApi.validateActiveUser(userId);
        PageResult<ProductDO> page = productMapper.selectPagePersonal(req, userId);
        return new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, Long id) {
        memberUserApi.validateActiveUserForUpdate(userId);
        ProductDO product = requireForUpdate(userId, id);
        requireCategory(product.getCategoryId());
        ProductSkuDO sku = skuMapper.selectListByProductIdForUpdate(id).stream().findFirst().orElse(null);
        if (sku == null || !Integer.valueOf(1).equals(sku.getStock()) || !CommonStatusEnum.isEnable(sku.getStatus())
                || !ProductSaleStatusEnum.OFF_SHELF.getStatus().equals(product.getSaleStatus())
                || (!ProductAuditStatusEnum.DRAFT.getStatus().equals(product.getAuditStatus())
                && !ProductAuditStatusEnum.REJECTED.getStatus().equals(product.getAuditStatus()))) {
            throw exception(PERSONAL_LISTING_STATE_INVALID);
        }
        if (productMapper.updateAuditExpected(id, product.getAuditStatus(), ProductAuditStatusEnum.PENDING.getStatus(), null, null, null) != 1) {
            throw exception(PERSONAL_LISTING_STATE_INVALID);
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void list(Long userId, Long id) {
        memberUserApi.validateActiveUserForUpdate(userId);
        ProductDO product = requireForUpdate(userId, id);
        ProductSkuDO sku = skuMapper.selectListByProductIdForUpdate(id).stream().findFirst().orElse(null);
        if (sku == null || !Integer.valueOf(1).equals(sku.getStock()) || !ProductAuditStatusEnum.APPROVED.getStatus().equals(product.getAuditStatus())) {
            throw exception(PERSONAL_LISTING_STATE_INVALID);
        }
        if (productMapper.updatePersonalSaleExpected(id, userId, ProductSaleStatusEnum.OFF_SHELF.getStatus(), ProductSaleStatusEnum.ON_SALE.getStatus()) != 1) {
            throw exception(PERSONAL_LISTING_STATE_INVALID);
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void delist(Long userId, Long id) {
        memberUserApi.validateActiveUserForUpdate(userId);
        requireForUpdate(userId, id);
        if (productMapper.updatePersonalSaleExpected(id, userId, ProductSaleStatusEnum.ON_SALE.getStatus(), ProductSaleStatusEnum.OFF_SHELF.getStatus()) != 1) {
            throw exception(PERSONAL_LISTING_STATE_INVALID);
        }
    }

    private ProductDO requireForUpdate(Long userId, Long id) {
        ProductDO product = productMapper.selectPersonalByIdForUpdate(id, userId);
        if (product == null) throw exception(PERSONAL_LISTING_NOT_FOUND);
        return product;
    }

    private void requireCategory(Long id) {
        Set<Long> seen = new HashSet<>();
        Long currentId = id;
        while (currentId != null) {
            if (!seen.add(currentId)) throw exception(PRODUCT_CATEGORY_TREE_INVALID);
            var category = categoryMapper.selectById(currentId);
            if (category == null) throw exception(PRODUCT_CATEGORY_TREE_INVALID);
            if (!CommonStatusEnum.isEnable(category.getStatus())) throw exception(PRODUCT_CATEGORY_DISABLED);
            currentId = category.getParentId();
        }
    }

    private void validatePrice(Long price) { if (price == null || price < 0) throw exception(PERSONAL_LISTING_PRICE_INVALID); }
    private String trim(String value) { String normalized = StrUtil.trim(value); if (StrUtil.isBlank(normalized)) throw exception(PERSONAL_LISTING_STATE_INVALID); return normalized; }
    private List<String> normalizeImages(List<String> values) {
        return values == null ? null : values.stream().map(value -> value == null ? null : StrUtil.trim(value))
                .filter(StrUtil::isNotBlank).distinct().toList();
    }
    private PersonalListingRespVO toResponse(ProductDO product) {
        ProductSkuDO sku = skuMapper.selectListByProductId(product.getId()).stream().findFirst().orElse(null);
        return new PersonalListingRespVO().setId(product.getId()).setCategoryId(product.getCategoryId()).setName(product.getName())
                .setCondition(product.getCondition()).setMainImageUrl(product.getMainImageUrl()).setImageUrls(product.getImageUrls())
                .setDescription(product.getDescription()).setPrice(sku == null ? null : sku.getPrice())
                .setAuditStatus(product.getAuditStatus()).setSaleStatus(product.getSaleStatus()).setStock(sku == null ? 0 : sku.getStock())
                .setRejectReason(product.getRejectReason()).setCreateTime(product.getCreateTime()).setUpdateTime(product.getUpdateTime());
    }
}
