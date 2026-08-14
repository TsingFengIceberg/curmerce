package cn.iocoder.yudao.module.commerce.service.product;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductBaseSaveReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductCreateOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductPageOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductRejectReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductReviewPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductSpecificationValueReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.enums.product.ProductAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class ProductServiceImpl implements ProductService {

    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private ProductCategoryService categoryService;
    @Resource private MerchantAccessService merchantAccessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOwnProduct(ProductCreateOwnReqVO reqVO) {
        MerchantAccessContext context = requireEnabledStore(reqVO.getStoreId());
        List<ProductCategoryDO> categories = categoryService.lockCategorySnapshot();
        categoryService.requireEnabledCategory(reqVO.getCategoryId(), categories);
        validateAggregate(reqVO);
        String code = normalizeProductCode(reqVO.getCode());
        if (productMapper.selectByMerchantIdAndCode(context.merchant().getId(), code) != null) {
            throw exception(PRODUCT_CODE_DUPLICATE);
        }
        ProductDO product = new ProductDO().setMerchantId(context.merchant().getId())
                .setStoreId(context.store().getId()).setCategoryId(reqVO.getCategoryId()).setCode(code)
                .setName(trimRequired(reqVO.getName())).setSubtitle(StrUtil.trim(reqVO.getSubtitle()))
                .setMainImageUrl(trimRequired(reqVO.getMainImageUrl()))
                .setImageUrls(normalizeImageUrls(reqVO.getImageUrls()))
                .setDescription(trimRequired(reqVO.getDescription()))
                .setAuditStatus(ProductAuditStatusEnum.DRAFT.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus())
                .setSort(reqVO.getSort());
        try {
            productMapper.insert(product);
        } catch (DuplicateKeyException ex) {
            throw exception(PRODUCT_CODE_DUPLICATE);
        }
        insertSkus(product, context.merchant().getId(), reqVO.getSkus());
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOwnProduct(ProductUpdateOwnReqVO reqVO) {
        MerchantAccessContext context = requireEnabledStore(reqVO.getStoreId());
        List<ProductCategoryDO> categories = categoryService.lockCategorySnapshot();
        categoryService.requireEnabledCategory(reqVO.getCategoryId(), categories);
        validateAggregate(reqVO);
        ProductDO current = productMapper.selectByIdAndMerchantIdForUpdate(reqVO.getId(), context.merchant().getId());
        if (current == null || !context.store().getId().equals(current.getStoreId())) {
            throw exception(PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED);
        }
        requireEditable(current);
        List<ProductSkuDO> currentSkus = skuMapper.selectListByProductIdAndMerchantIdForUpdate(
                current.getId(), context.merchant().getId());
        Map<Long, ProductSkuDO> currentById = new HashMap<>();
        currentSkus.forEach(item -> currentById.put(item.getId(), item));
        Set<Long> requestedIds = new HashSet<>();
        Set<String> requestedCodes = new HashSet<>();
        Set<String> currentCodes = currentSkus.stream().map(ProductSkuDO::getCode).collect(java.util.stream.Collectors.toSet());
        for (ProductSkuSaveReqVO skuReq : reqVO.getSkus()) {
            validateSku(skuReq);
            String code = normalizeSkuCode(skuReq.getCode());
            if (!requestedCodes.add(code)) {
                throw exception(PRODUCT_SKU_CODE_REQUEST_DUPLICATE);
            }
            if (skuReq.getId() != null) {
                if (!requestedIds.add(skuReq.getId())) {
                    throw exception(PRODUCT_SKU_ID_DUPLICATE);
                }
                ProductSkuDO currentSku = currentById.get(skuReq.getId());
                if (currentSku == null) {
                    throw exception(PRODUCT_SKU_ID_INVALID);
                }
                if (!currentSku.getCode().equals(code)) {
                    throw exception(PRODUCT_SKU_CODE_IMMUTABLE);
                }
            } else {
                if (currentCodes.contains(code)
                        || skuMapper.selectByMerchantIdAndCode(context.merchant().getId(), code) != null) {
                    throw exception(PRODUCT_SKU_CODE_DUPLICATE);
                }
            }
        }
        ProductDO update = new ProductDO().setId(current.getId()).setMerchantId(current.getMerchantId())
                .setStoreId(context.store().getId()).setCategoryId(reqVO.getCategoryId())
                .setName(trimRequired(reqVO.getName())).setSubtitle(StrUtil.trim(reqVO.getSubtitle()))
                .setMainImageUrl(trimRequired(reqVO.getMainImageUrl()))
                .setImageUrls(normalizeImageUrls(reqVO.getImageUrls()))
                .setDescription(trimRequired(reqVO.getDescription())).setSort(reqVO.getSort());
        if (productMapper.updateOwnFields(current.getId(), context.merchant().getId(), update) != 1) {
            throw exception(PRODUCT_STATE_CONFLICT);
        }
        Set<Long> retainedIds = new HashSet<>();
        for (ProductSkuSaveReqVO skuReq : reqVO.getSkus()) {
            ProductSkuDO sku = toSku(skuReq, current.getId(), context.merchant().getId());
            try {
                if (skuReq.getId() == null) {
                    skuMapper.insert(sku);
                } else {
                    retainedIds.add(skuReq.getId());
                    if (skuMapper.updateOwned(sku) != 1) {
                        throw exception(PRODUCT_STATE_CONFLICT);
                    }
                }
            } catch (DuplicateKeyException ex) {
                throw exception(PRODUCT_SKU_CODE_DUPLICATE);
            }
        }
        List<Long> omittedIds = currentSkus.stream().map(ProductSkuDO::getId)
                .filter(id -> !retainedIds.contains(id)).toList();
        skuMapper.deleteByIdsAndOwnership(omittedIds, current.getId(), context.merchant().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductAggregate getOwnProduct(Long id) {
        MerchantAccessContext context = requireEnabledStore(null);
        ProductDO product = productMapper.selectByIdAndMerchantId(id, context.merchant().getId());
        if (product == null || !context.store().getId().equals(product.getStoreId())) {
            throw exception(PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED);
        }
        return new ProductAggregate(product, skuMapper.selectListByProductIdAndMerchantId(product.getId(),
                context.merchant().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductAggregate> getOwnProductPage(ProductPageOwnReqVO reqVO) {
        MerchantAccessContext context = requireEnabledStore(reqVO.getStoreId());
        PageResult<ProductDO> page = productMapper.selectPageOwn(reqVO, context.merchant().getId());
        return new PageResult<>(page.getList().stream().map(product -> new ProductAggregate(product, List.of())).toList(),
                page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitOwnProduct(Long id) {
        MerchantAccessContext context = requireEnabledStore(null);
        List<ProductCategoryDO> categories = categoryService.lockCategorySnapshot();
        ProductDO product = requireOwnedProductForUpdate(id, context);
        categoryService.requireEnabledCategory(product.getCategoryId(), categories);
        List<ProductSkuDO> skus = skuMapper.selectListByProductIdAndMerchantIdForUpdate(id,
                context.merchant().getId());
        if (skus.isEmpty()) throw exception(PRODUCT_SKU_COUNT_INVALID);
        if (!ProductAuditStatusEnum.DRAFT.getStatus().equals(product.getAuditStatus())
                && !ProductAuditStatusEnum.REJECTED.getStatus().equals(product.getAuditStatus())) {
            throw exception(PRODUCT_AUDIT_STATE_INVALID);
        }
        if (!ProductSaleStatusEnum.OFF_SHELF.getStatus().equals(product.getSaleStatus())) {
            throw exception(PRODUCT_SALE_STATE_INVALID);
        }
        if (productMapper.updateAuditExpected(id, product.getAuditStatus(), ProductAuditStatusEnum.PENDING.getStatus(),
                null, null, null) != 1) {
            throw exception(PRODUCT_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void listOwnProduct(Long id) {
        MerchantAccessContext context = requireEnabledStore(null);
        List<ProductCategoryDO> categories = categoryService.lockCategorySnapshot();
        ProductDO product = requireOwnedProductForUpdate(id, context);
        List<ProductSkuDO> skus = skuMapper.selectListByProductIdAndMerchantIdForUpdate(id,
                context.merchant().getId());
        categoryService.requireEnabledCategory(product.getCategoryId(), categories);
        if (!ProductAuditStatusEnum.APPROVED.getStatus().equals(product.getAuditStatus())
                || !ProductSaleStatusEnum.OFF_SHELF.getStatus().equals(product.getSaleStatus())) {
            throw exception(PRODUCT_SALE_STATE_INVALID);
        }
        if (skus.stream().noneMatch(sku -> CommonStatusEnum.ENABLE.getStatus().equals(sku.getStatus())
                && sku.getStock() != null && sku.getStock() > 0)) {
            throw exception(PRODUCT_NO_SELLABLE_SKU);
        }
        if (productMapper.updateSaleExpected(id, context.merchant().getId(), ProductSaleStatusEnum.OFF_SHELF.getStatus(),
                ProductSaleStatusEnum.ON_SALE.getStatus()) != 1) {
            throw exception(PRODUCT_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delistOwnProduct(Long id) {
        MerchantAccessContext context = requireEnabledStore(null);
        ProductDO product = requireOwnedProductForUpdate(id, context);
        if (!ProductAuditStatusEnum.APPROVED.getStatus().equals(product.getAuditStatus())
                || !ProductSaleStatusEnum.ON_SALE.getStatus().equals(product.getSaleStatus())) {
            throw exception(PRODUCT_SALE_STATE_INVALID);
        }
        if (productMapper.updateSaleExpected(id, context.merchant().getId(), ProductSaleStatusEnum.ON_SALE.getStatus(),
                ProductSaleStatusEnum.OFF_SHELF.getStatus()) != 1) {
            throw exception(PRODUCT_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProductAggregate getProductForReview(Long id) {
        ProductDO product = productMapper.selectById(id);
        if (product == null) throw exception(PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED);
        return new ProductAggregate(product, skuMapper.selectListByProductId(product.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductAggregate> getProductReviewPage(ProductReviewPageReqVO reqVO) {
        PageResult<ProductDO> page = productMapper.selectReviewPage(reqVO);
        return new PageResult<>(page.getList().stream().map(product -> new ProductAggregate(product, List.of())).toList(),
                page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveProduct(Long id) {
        ProductDO product = requireReviewProductForUpdate(id);
        if (!ProductAuditStatusEnum.PENDING.getStatus().equals(product.getAuditStatus())
                || !ProductSaleStatusEnum.OFF_SHELF.getStatus().equals(product.getSaleStatus())) {
            throw exception(PRODUCT_AUDIT_STATE_INVALID);
        }
        Long reviewerId = getLoginUserId();
        if (reviewerId == null) throw exception(PRODUCT_STATE_CONFLICT);
        if (productMapper.updateAuditExpected(id, ProductAuditStatusEnum.PENDING.getStatus(),
                ProductAuditStatusEnum.APPROVED.getStatus(), reviewerId, LocalDateTime.now(), null) != 1) {
            throw exception(PRODUCT_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectProduct(ProductRejectReqVO reqVO) {
        ProductDO product = requireReviewProductForUpdate(reqVO.getId());
        if (!ProductAuditStatusEnum.PENDING.getStatus().equals(product.getAuditStatus())
                || !ProductSaleStatusEnum.OFF_SHELF.getStatus().equals(product.getSaleStatus())) {
            throw exception(PRODUCT_AUDIT_STATE_INVALID);
        }
        String reason = StrUtil.trim(reqVO.getReason());
        if (reason == null || reason.isEmpty() || reason.length() > 255) {
            throw exception(PRODUCT_AUDIT_STATE_INVALID);
        }
        Long reviewerId = getLoginUserId();
        if (reviewerId == null) throw exception(PRODUCT_STATE_CONFLICT);
        if (productMapper.updateAuditExpected(reqVO.getId(), ProductAuditStatusEnum.PENDING.getStatus(),
                ProductAuditStatusEnum.REJECTED.getStatus(), reviewerId, LocalDateTime.now(), reason) != 1) {
            throw exception(PRODUCT_STATE_CONFLICT);
        }
    }

    private MerchantAccessContext requireEnabledStore(Long requestedStoreId) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        if (requestedStoreId != null && !context.store().getId().equals(requestedStoreId)) {
            throw exception(PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED);
        }
        if (!CommonStatusEnum.ENABLE.getStatus().equals(context.store().getStatus())) {
            throw exception(PRODUCT_STORE_DISABLED);
        }
        return context;
    }

    private ProductDO requireOwnedProductForUpdate(Long id, MerchantAccessContext context) {
        ProductDO product = productMapper.selectByIdAndMerchantIdForUpdate(id, context.merchant().getId());
        if (product == null || !context.store().getId().equals(product.getStoreId())) {
            throw exception(PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED);
        }
        return product;
    }

    private ProductDO requireReviewProductForUpdate(Long id) {
        ProductDO product = productMapper.selectByIdForUpdate(id);
        if (product == null) throw exception(PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED);
        return product;
    }

    private void requireEditable(ProductDO product) {
        if (!ProductSaleStatusEnum.OFF_SHELF.getStatus().equals(product.getSaleStatus())
                || (!ProductAuditStatusEnum.DRAFT.getStatus().equals(product.getAuditStatus())
                && !ProductAuditStatusEnum.REJECTED.getStatus().equals(product.getAuditStatus()))) {
            throw exception(PRODUCT_EDIT_STATE_INVALID);
        }
    }

    private void validateAggregate(ProductBaseSaveReqVO reqVO) {
        if (reqVO.getSkus() == null || reqVO.getSkus().isEmpty() || reqVO.getSkus().size() > 100) {
            throw exception(PRODUCT_SKU_COUNT_INVALID);
        }
        Set<String> imageUrls = new HashSet<>();
        if (reqVO.getImageUrls() != null) {
            for (String imageUrl : reqVO.getImageUrls()) {
                if (imageUrl == null || imageUrl.isBlank() || !imageUrls.add(imageUrl.trim())) {
                    throw exception(PRODUCT_SKU_SPECIFICATION_INVALID);
                }
            }
        }
        Set<String> codes = new HashSet<>();
        for (ProductSkuSaveReqVO sku : reqVO.getSkus()) {
            validateSku(sku);
            if (!codes.add(normalizeSkuCode(sku.getCode()))) {
                throw exception(PRODUCT_SKU_CODE_REQUEST_DUPLICATE);
            }
        }
    }

    private void validateSku(ProductSkuSaveReqVO sku) {
        String code = normalizeSkuCode(sku.getCode());
        if (sku.getStatus() == null || (!CommonStatusEnum.isEnable(sku.getStatus())
                && !CommonStatusEnum.isDisable(sku.getStatus()))) {
            throw exception(PRODUCT_SKU_STATUS_INVALID);
        }
        if (sku.getPrice() == null || sku.getPrice() < 0 || sku.getStock() == null || sku.getStock() < 0
                || sku.getSort() == null || sku.getSort() < 0
                || (sku.getMarketPrice() != null && sku.getMarketPrice() < sku.getPrice())) {
            throw exception(PRODUCT_SKU_PRICE_INVALID);
        }
        Set<String> names = new HashSet<>();
        if (sku.getSpecificationValues() != null) {
            for (ProductSpecificationValueReqVO value : sku.getSpecificationValues()) {
                if (value == null || value.getName() == null || value.getValue() == null
                        || value.getName().isBlank() || value.getValue().isBlank()
                        || !names.add(value.getName().trim())) {
                    throw exception(PRODUCT_SKU_SPECIFICATION_INVALID);
                }
            }
        }
    }

    private void insertSkus(ProductDO product, Long merchantId, List<ProductSkuSaveReqVO> requests) {
        for (ProductSkuSaveReqVO request : requests) {
            try {
                skuMapper.insert(toSku(request, product.getId(), merchantId));
            } catch (DuplicateKeyException ex) {
                throw exception(PRODUCT_SKU_CODE_DUPLICATE);
            }
        }
    }

    private ProductSkuDO toSku(ProductSkuSaveReqVO request, Long productId, Long merchantId) {
        List<ProductSkuDO.SpecificationValue> specifications = new ArrayList<>();
        if (request.getSpecificationValues() != null) {
            for (ProductSpecificationValueReqVO value : request.getSpecificationValues()) {
                specifications.add(new ProductSkuDO.SpecificationValue(StrUtil.trim(value.getName()),
                        StrUtil.trim(value.getValue())));
            }
        }
        return new ProductSkuDO().setId(request.getId()).setProductId(productId).setMerchantId(merchantId)
                .setCode(normalizeSkuCode(request.getCode())).setSpecificationValues(specifications)
                .setImageUrl(StrUtil.trim(request.getImageUrl())).setPrice(request.getPrice())
                .setMarketPrice(request.getMarketPrice()).setStock(request.getStock()).setStatus(request.getStatus())
                .setSort(request.getSort());
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) return null;
        return imageUrls.stream().map(String::trim).toList();
    }

    private String normalizeProductCode(String code) {
        String normalized = StrUtil.trim(code);
        if (normalized == null || !normalized.matches("[a-z0-9_-]{2,64}")) {
            throw exception(PRODUCT_CODE_DUPLICATE);
        }
        return normalized;
    }

    private String normalizeSkuCode(String code) {
        String normalized = StrUtil.trim(code);
        if (normalized == null || !normalized.matches("[a-z0-9_-]{2,64}")) {
            throw exception(PRODUCT_SKU_CODE_DUPLICATE);
        }
        return normalized;
    }

    private String trimRequired(String value) {
        String normalized = StrUtil.trim(value);
        if (normalized == null || normalized.isEmpty()) {
            throw exception(PRODUCT_SKU_SPECIFICATION_INVALID);
        }
        return normalized;
    }
}
