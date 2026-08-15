package cn.iocoder.yudao.module.commerce.service.catalog;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.*;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.*;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.*;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class PublicCatalogServiceImpl implements PublicCatalogService {
    @Resource private ProductCategoryMapper categoryMapper;
    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private StoreMapper storeMapper;
    @Resource private MerchantMapper merchantMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PublicCategoryNodeRespVO> getCategoryTree() {
        Map<Long, ProductCategoryDO> all = categorySnapshot();
        Map<Long, PublicCategoryNodeRespVO> responses = new HashMap<>();
        List<PublicCategoryNodeRespVO> roots = new ArrayList<>();
        List<ProductCategoryDO> publicCategories = all.values().stream().filter(this::enabledCategory)
                .filter(category -> ancestorsEnabled(category, all)).sorted(categoryOrder()).toList();
        publicCategories.stream()
                .forEach(category -> {
                    PublicCategoryNodeRespVO node = toCategory(category);
                    responses.put(category.getId(), node);
                });
        publicCategories.forEach(category -> {
            PublicCategoryNodeRespVO node = responses.get(category.getId());
            if (node.getParentId() == null) roots.add(node);
            else {
                PublicCategoryNodeRespVO parent = responses.get(node.getParentId());
                if (parent != null) parent.getChildren().add(node);
            }
        });
        return roots;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PublicProductSummaryRespVO> getProductPage(PublicProductPageReqVO reqVO) {
        Map<Long, ProductCategoryDO> categories = categorySnapshot();
        Set<Long> publicCategoryIds = categories.values().stream()
                .filter(this::enabledCategory)
                .filter(category -> ancestorsEnabled(category, categories))
                .map(ProductCategoryDO::getId)
                .collect(Collectors.toSet());
        Set<Long> categoryIds = publicCategoryIds;
        if (reqVO.getCategoryId() != null) {
            ProductCategoryDO category = categories.get(reqVO.getCategoryId());
            if (category == null || !ancestorsEnabled(category, categories)) return PageResult.empty();
            categoryIds = categories.values().stream().filter(item -> ancestorsEnabled(item, categories)
                    && isDescendantOrSelf(item.getId(), reqVO.getCategoryId(), categories)).map(ProductCategoryDO::getId)
                    .collect(Collectors.toSet());
        }
        if (categoryIds.isEmpty()) return PageResult.empty();
        String keyword = StrUtil.trim(reqVO.getKeyword());
        PageResult<ProductDO> page = productMapper.selectPublicPage(reqVO, categoryIds,
                StrUtil.isBlank(keyword) ? null : keyword);
        List<PublicProductSummaryRespVO> summaries = page.getList().stream()
                .map(product -> buildSummary(product, categories))
                .filter(Objects::nonNull)
                .toList();
        return new PageResult<>(summaries, page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProductDetailRespVO getProductDetail(Long id) {
        ProductDO product = productMapper.selectById(id);
        PublicProductSummaryRespVO summary = product == null ? null : buildSummary(product, categorySnapshot());
        if (summary == null) throw exception(PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED);
        PublicProductDetailRespVO detail = new PublicProductDetailRespVO();
        detail.setId(summary.getId()); detail.setCategoryId(summary.getCategoryId()); detail.setStoreId(summary.getStoreId());
        detail.setStoreName(summary.getStoreName()); detail.setName(summary.getName()); detail.setSubtitle(summary.getSubtitle());
        detail.setMainImageUrl(summary.getMainImageUrl()); detail.setMinPrice(summary.getMinPrice());
        detail.setMinMarketPrice(summary.getMinMarketPrice()); detail.setTotalStock(summary.getTotalStock()); detail.setAvailable(summary.getAvailable());
        detail.setImageUrls(product.getImageUrls()); detail.setDescription(product.getDescription());
        detail.setSkus(toSkuResponses(skuMapper.selectPublicListByProductId(product.getId()), product.getMainImageUrl()));
        return detail;
    }

    @Override
    public PublicProductSummaryRespVO getVisibleSummary(Long productId, Long skuId) {
        ProductDO product = productMapper.selectById(productId);
        if (product == null || (skuId != null && skuMapper.selectById(skuId) == null)) return null;
        PublicProductSummaryRespVO summary = buildSummary(product, categorySnapshot());
        if (summary == null) return null;
        if (skuId != null && skuMapper.selectPublicListByProductId(productId).stream().noneMatch(s -> Objects.equals(s.getId(), skuId))) return null;
        return summary;
    }

    @Override
    public ProductSkuDO getVisibleSku(Long skuId) {
        ProductSkuDO sku = skuMapper.selectById(skuId);
        if (sku == null) return null;
        return getVisibleSummary(sku.getProductId(), skuId) == null ? null : sku;
    }

    private PublicProductSummaryRespVO buildSummary(ProductDO product, Map<Long, ProductCategoryDO> categories) {
        if (!Objects.equals(product.getAuditStatus(), 2) || !Objects.equals(product.getSaleStatus(), 1)) return null;
        ProductCategoryDO category = categories.get(product.getCategoryId());
        if (category == null || !ancestorsEnabled(category, categories)) return null;
        MerchantDO merchant = merchantMapper.selectById(product.getMerchantId());
        StoreDO store = storeMapper.selectById(product.getStoreId());
        if (merchant == null || !Objects.equals(merchant.getStatus(), MerchantAuditStatusEnum.APPROVED.getStatus())
                || store == null || !Objects.equals(store.getMerchantId(), merchant.getId())
                || !Objects.equals(store.getStatus(), CommonStatusEnum.ENABLE.getStatus())) return null;
        List<ProductSkuDO> skus = skuMapper.selectPublicListByProductId(product.getId());
        if (skus.isEmpty()) return null;
        long minPrice = skus.stream().map(ProductSkuDO::getPrice).min(Long::compareTo).orElse(0L);
        Long minMarket = skus.stream().map(ProductSkuDO::getMarketPrice).filter(Objects::nonNull).min(Long::compareTo).orElse(null);
        int stock = skus.stream().mapToInt(s -> s.getStock() == null ? 0 : s.getStock()).sum();
        PublicProductSummaryRespVO response = new PublicProductSummaryRespVO();
        response.setId(product.getId()); response.setCategoryId(product.getCategoryId()); response.setStoreId(store.getId());
        response.setStoreName(store.getName()); response.setName(product.getName()); response.setSubtitle(product.getSubtitle());
        response.setMainImageUrl(product.getMainImageUrl()); response.setMinPrice(minPrice); response.setMinMarketPrice(minMarket);
        response.setTotalStock(stock); response.setAvailable(stock > 0);
        return response;
    }
    private List<PublicProductSkuRespVO> toSkuResponses(List<ProductSkuDO> skus, String fallbackImage) {
        return skus.stream().map(sku -> {
            PublicProductSkuRespVO item = new PublicProductSkuRespVO();
            item.setId(sku.getId()); item.setSpecificationValues(sku.getSpecificationValues());
            item.setImageUrl(StrUtil.blankToDefault(sku.getImageUrl(), fallbackImage)); item.setPrice(sku.getPrice());
            item.setMarketPrice(sku.getMarketPrice()); item.setStock(sku.getStock()); item.setAvailable(sku.getStock() != null && sku.getStock() > 0);
            return item;
        }).toList();
    }
    private Map<Long, ProductCategoryDO> categorySnapshot() {
        List<ProductCategoryDO> list = categoryMapper.selectListOrdered();
        Map<Long, ProductCategoryDO> map = list.stream().collect(Collectors.toMap(ProductCategoryDO::getId, x -> x, (a, b) -> { throw exception(PRODUCT_CATEGORY_TREE_INVALID); }));
        for (ProductCategoryDO category : list) {
            Set<Long> seen = new HashSet<>(); Long current = category.getId();
            while (current != null) { if (!seen.add(current)) throw exception(PRODUCT_CATEGORY_TREE_INVALID); ProductCategoryDO node = map.get(current); if (node == null) throw exception(PRODUCT_CATEGORY_TREE_INVALID); current = node.getParentId(); }
        }
        return map;
    }
    private boolean enabledCategory(ProductCategoryDO category) { return Objects.equals(category.getStatus(), CommonStatusEnum.ENABLE.getStatus()); }
    private boolean ancestorsEnabled(ProductCategoryDO category, Map<Long, ProductCategoryDO> all) {
        Long id = category.getId(); Set<Long> seen = new HashSet<>();
        while (id != null) { if (!seen.add(id)) return false; ProductCategoryDO node = all.get(id); if (node == null || !enabledCategory(node)) return false; id = node.getParentId(); }
        return true;
    }
    private boolean isDescendantOrSelf(Long id, Long ancestor, Map<Long, ProductCategoryDO> all) {
        Set<Long> seen = new HashSet<>(); while (id != null) { if (!seen.add(id)) return false; if (Objects.equals(id, ancestor)) return true; ProductCategoryDO node = all.get(id); id = node == null ? null : node.getParentId(); } return false;
    }
    private Comparator<ProductCategoryDO> categoryOrder() { return Comparator.comparing(ProductCategoryDO::getSort).thenComparing(ProductCategoryDO::getId); }
    private PublicCategoryNodeRespVO toCategory(ProductCategoryDO category) { PublicCategoryNodeRespVO n = new PublicCategoryNodeRespVO(); n.setId(category.getId()); n.setParentId(category.getParentId()); n.setName(category.getName()); n.setImageUrl(category.getImageUrl()); return n; }
}
