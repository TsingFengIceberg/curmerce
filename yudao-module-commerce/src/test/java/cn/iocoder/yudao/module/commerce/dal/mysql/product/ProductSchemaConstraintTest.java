package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductSchemaConstraintTest extends BaseDbUnitTest {

    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private StoreMapper storeMapper;
    @Resource
    private ProductCategoryMapper categoryMapper;
    @Resource
    private ProductMapper productMapper;
    @Resource
    private ProductSkuMapper skuMapper;

    @Test
    void rejectsCrossMerchantStoreOwnership() {
        MerchantStore first = createMerchantStore("merchant_one");
        MerchantStore second = createMerchantStore("merchant_two");
        ProductCategoryDO category = createCategory("category_one");

        assertConstraint(() -> productMapper.insert(validProduct(first, category, "product_one")
                .setStoreId(second.store().getId())));
    }

    @Test
    void rejectsSkuWithDifferentMerchantThanProduct() {
        MerchantStore first = createMerchantStore("merchant_one");
        MerchantStore second = createMerchantStore("merchant_two");
        ProductDO product = validProduct(first, createCategory("category_one"), "product_one");
        productMapper.insert(product);

        assertConstraint(() -> skuMapper.insert(new ProductSkuDO().setProductId(product.getId())
                .setMerchantId(second.merchant().getId()).setCode("sku_wrong_owner")
                .setPrice(100L).setStock(1).setStatus(0).setSort(0)));
    }

    @Test
    void rejectsNegativeMoneyStockAndUnknownStatus() {
        MerchantStore merchant = createMerchantStore("merchant_one");
        ProductCategoryDO category = createCategory("category_one");
        ProductDO product = validProduct(merchant, category, "product_one");
        productMapper.insert(product);

        assertConstraint(() -> skuMapper.insert(new ProductSkuDO().setProductId(product.getId())
                .setMerchantId(merchant.merchant().getId()).setCode("sku_negative_price")
                .setPrice(-1L).setStock(1).setStatus(0).setSort(0)));
        assertConstraint(() -> skuMapper.insert(new ProductSkuDO().setProductId(product.getId())
                .setMerchantId(merchant.merchant().getId()).setCode("sku_negative_stock")
                .setPrice(100L).setStock(-1).setStatus(0).setSort(0)));
        assertConstraint(() -> skuMapper.insert(new ProductSkuDO().setProductId(product.getId())
                .setMerchantId(merchant.merchant().getId()).setCode("sku_negative_market_price")
                .setPrice(100L).setMarketPrice(-1L).setStock(1).setStatus(0).setSort(0)));
        assertConstraint(() -> skuMapper.insert(new ProductSkuDO().setProductId(product.getId())
                .setMerchantId(merchant.merchant().getId()).setCode("sku_negative_sort")
                .setPrice(100L).setStock(1).setStatus(0).setSort(-1)));
        assertConstraint(() -> skuMapper.insert(new ProductSkuDO().setProductId(product.getId())
                .setMerchantId(merchant.merchant().getId()).setCode("sku_unknown_status")
                .setPrice(100L).setStock(1).setStatus(7).setSort(0)));
        assertConstraint(() -> categoryMapper.insert(new ProductCategoryDO().setCode("negative_sort")
                .setName("Negative sort").setStatus(0).setSort(-1)));
        assertConstraint(() -> categoryMapper.insert(new ProductCategoryDO().setCode("unknown_status")
                .setName("Unknown status").setStatus(7).setSort(0)));
        assertConstraint(() -> productMapper.insert(validProduct(merchant, category, "product_negative_sort")
                .setSort(-1)));
        assertConstraint(() -> productMapper.insert(validProduct(merchant, category, "product_unknown_audit")
                .setAuditStatus(7)));
        assertConstraint(() -> productMapper.insert(validProduct(merchant, category, "product_unknown_sale")
                .setAuditStatus(2).setSaleStatus(7).setReviewerUserId(99L).setReviewTime(LocalDateTime.now())));
    }

    @Test
    void rejectsInvalidSaleStateAndReviewMetadata() {
        MerchantStore merchant = createMerchantStore("merchant_one");
        ProductCategoryDO category = createCategory("category_one");

        assertConstraint(() -> productMapper.insert(validProduct(merchant, category, "product_on_draft")
                .setSaleStatus(1)));
        assertConstraint(() -> productMapper.insert(validProduct(merchant, category, "product_approved_missing_review")
                .setAuditStatus(2)));
        assertConstraint(() -> productMapper.insert(validProduct(merchant, category, "product_draft_with_reviewer")
                .setReviewerUserId(99L)));
        assertConstraint(() -> productMapper.insert(validProduct(merchant, category, "product_rejected_blank_reason")
                .setAuditStatus(3).setReviewerUserId(99L).setReviewTime(LocalDateTime.now()).setRejectReason(" ")));

        productMapper.insert(validProduct(merchant, category, "product_approved")
                .setAuditStatus(2).setSaleStatus(1).setReviewerUserId(99L).setReviewTime(LocalDateTime.now()));
        productMapper.insert(validProduct(merchant, category, "product_rejected")
                .setAuditStatus(3).setReviewerUserId(99L).setReviewTime(LocalDateTime.now())
                .setRejectReason("Insufficient detail"));
    }

    @Test
    void permanentlyReservesCodesAfterLogicalDelete() {
        MerchantStore merchant = createMerchantStore("merchant_one");
        ProductCategoryDO deletedCategory = createCategory("category_one");
        categoryMapper.deleteById(deletedCategory.getId());
        assertConstraint(() -> categoryMapper.insert(new ProductCategoryDO().setCode("category_one")
                .setName("Duplicate").setStatus(0).setSort(0)));

        ProductCategoryDO activeCategory = createCategory("category_active");
        ProductDO product = validProduct(merchant, activeCategory, "product_one");
        productMapper.insert(product);
        productMapper.deleteById(product.getId());
        assertConstraint(() -> productMapper.insert(validProduct(merchant, activeCategory, "product_one")));
    }

    @Test
    void skuCodesAreMerchantScopedAndReservedAfterLogicalDelete() {
        MerchantStore first = createMerchantStore("merchant_one");
        MerchantStore second = createMerchantStore("merchant_two");
        ProductCategoryDO category = createCategory("category_one");
        ProductDO firstProduct = validProduct(first, category, "product_one");
        ProductDO secondProduct = validProduct(second, category, "product_two");
        productMapper.insert(firstProduct);
        productMapper.insert(secondProduct);

        ProductSkuDO firstSku = validSku(firstProduct, first, "shared_sku");
        skuMapper.insert(firstSku);
        skuMapper.insert(validSku(secondProduct, second, "shared_sku"));
        skuMapper.deleteById(firstSku.getId());

        assertConstraint(() -> skuMapper.insert(validSku(firstProduct, first, "shared_sku")));
    }

    @Test
    void rejectsMissingCategoryReference() {
        MerchantStore merchant = createMerchantStore("merchant_one");
        assertConstraint(() -> productMapper.insert(validProduct(merchant, null, "product_missing_category")
                .setCategoryId(999999L)));
        assertConstraint(() -> skuMapper.insert(new ProductSkuDO().setProductId(999999L)
                .setMerchantId(merchant.merchant().getId()).setCode("sku_missing_product")
                .setPrice(100L).setStock(1).setStatus(0).setSort(0)));
    }

    private void assertConstraint(Runnable action) {
        assertThrows(DataIntegrityViolationException.class, action::run);
    }

    private ProductCategoryDO createCategory(String code) {
        ProductCategoryDO category = new ProductCategoryDO().setCode(code).setName("Category")
                .setSort(0).setStatus(CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(category);
        return category;
    }

    private MerchantStore createMerchantStore(String code) {
        MerchantDO merchant = new MerchantDO().setName(code).setCode(code)
                .setContactName("Contact").setContactMobile("13800138000")
                .setDefaultStoreName(code + " store").setDefaultStoreCode(code + "_store")
                .setStatus(MerchantAuditStatusEnum.APPROVED.getStatus());
        merchantMapper.insert(merchant);
        StoreDO store = new StoreDO().setMerchantId(merchant.getId()).setName(code + " store")
                .setCode(code + "_store").setContactName("Contact").setContactMobile("13800138000")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        storeMapper.insert(store);
        return new MerchantStore(merchant, store);
    }

    private ProductDO validProduct(MerchantStore merchant, ProductCategoryDO category, String code) {
        return new ProductDO().setMerchantId(merchant.merchant().getId()).setStoreId(merchant.store().getId())
                .setCategoryId(category == null ? null : category.getId()).setCode(code).setName("Product")
                .setMainImageUrl("https://img.example/main.jpg").setDescription("Description")
                .setAuditStatus(0).setSaleStatus(0).setSort(0);
    }

    private ProductSkuDO validSku(ProductDO product, MerchantStore merchant, String code) {
        return new ProductSkuDO().setProductId(product.getId()).setMerchantId(merchant.merchant().getId())
                .setCode(code).setPrice(100L).setStock(1).setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setSort(0);
    }

    private record MerchantStore(MerchantDO merchant, StoreDO store) {
    }
}
