package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductCreateOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductCategoryMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.PRODUCT_SKU_CODE_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@Import({ProductServiceImpl.class})
class ProductAggregateTransactionTest extends BaseDbUnitTest {

    @Resource private ProductService productService;
    @Resource private MerchantMapper merchantMapper;
    @Resource private StoreMapper storeMapper;
    @Resource private ProductCategoryMapper categoryMapper;
    @Resource private ProductMapper productMapper;
    @Resource private ProductSkuMapper skuMapper;
    @MockitoBean private MerchantAccessService merchantAccessService;
    @MockitoBean private ProductCategoryService categoryService;
    @MockitoBean private ProductOperationLogService operationLogService;

    private MerchantDO merchant;
    private StoreDO store;
    private ProductCategoryDO category;

    @BeforeEach
    void setUpAggregate() {
        merchant = merchantMapper.insert(new MerchantDO().setName("Merchant").setCode("merchant")
                .setContactName("Owner").setContactMobile("13800138000")
                .setDefaultStoreName("Store").setDefaultStoreCode("store")
                .setStatus(MerchantAuditStatusEnum.APPROVED.getStatus())) == 1
                ? merchantMapper.selectByCode("merchant") : null;
        store = new StoreDO().setMerchantId(merchant.getId()).setName("Store").setCode("store")
                .setContactName("Owner").setContactMobile("13800138000")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        storeMapper.insert(store);
        category = new ProductCategoryDO().setCode("category").setName("Category").setSort(0)
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(category);
        when(merchantAccessService.requireApprovedOwner()).thenReturn(
                new MerchantAccessContext(merchant, store));
        when(categoryService.lockCategorySnapshot()).thenReturn(List.of(category));
    }

    @Test
    void childDuplicateRollsBackTheParentProduct() {
        ProductDO existing = product("existing_product");
        productMapper.insert(existing);
        skuMapper.insert(sku(existing.getId(), "sku_taken"));

        ServiceException error = assertThrows(ServiceException.class, () -> productService.createOwnProduct(
                createRequest("new_product", "sku_taken")));
        assertEquals(PRODUCT_SKU_CODE_DUPLICATE.getCode(), error.getCode());
        assertNull(productMapper.selectByMerchantIdAndCode(merchant.getId(), "new_product"));
    }

    @Test
    void updateUsesCompleteSkuSetAndLogicallyDeletesOmittedRows() {
        ProductDO product = product("editable_product");
        productMapper.insert(product);
        ProductSkuDO retained = sku(product.getId(), "sku_retained");
        ProductSkuDO omitted = sku(product.getId(), "sku_omitted");
        skuMapper.insert(retained);
        skuMapper.insert(omitted);

        ProductUpdateOwnReqVO request = new ProductUpdateOwnReqVO().setId(product.getId());
        request.setStoreId(store.getId()).setCategoryId(category.getId()).setName("Updated")
                .setMainImageUrl("main.jpg").setDescription("Description").setSort(0)
                .setSkus(List.of(new ProductSkuSaveReqVO().setId(retained.getId()).setCode("sku_retained")
                        .setPrice(110L).setStock(3).setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0),
                        new ProductSkuSaveReqVO().setCode("sku_new").setPrice(120L).setStock(1)
                                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(1)));

        productService.updateOwnProduct(request);
        List<ProductSkuDO> active = skuMapper.selectListByProductIdAndMerchantId(product.getId(), merchant.getId());
        assertEquals(List.of("sku_retained", "sku_new"), active.stream().map(ProductSkuDO::getCode).toList());
        assertEquals("Updated", productMapper.selectById(product.getId()).getName());
    }

    @Test
    void updateExplicitlyClearsNullableProductAndSkuFields() {
        ProductDO product = product("nullable_fields")
                .setSubtitle("Old subtitle").setImageUrls(List.of("old.jpg"));
        productMapper.insert(product);
        ProductSkuDO sku = sku(product.getId(), "sku_nullable").setSpecificationValues(
                List.of(new ProductSkuDO.SpecificationValue("color", "blue")))
                .setImageUrl("old-sku.jpg").setMarketPrice(120L);
        skuMapper.insert(sku);

        ProductUpdateOwnReqVO request = new ProductUpdateOwnReqVO().setId(product.getId());
        request.setStoreId(store.getId()).setCategoryId(category.getId()).setName("Updated")
                .setMainImageUrl("main.jpg").setDescription("Description").setSort(0)
                .setSkus(List.of(new ProductSkuSaveReqVO().setId(sku.getId()).setCode("sku_nullable")
                        .setPrice(100L).setStock(1).setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0)));

        productService.updateOwnProduct(request);

        ProductDO reloadedProduct = productMapper.selectById(product.getId());
        ProductSkuDO reloadedSku = skuMapper.selectById(sku.getId());
        assertNull(reloadedProduct.getSubtitle());
        assertNull(reloadedProduct.getImageUrls());
        assertNull(reloadedSku.getImageUrl());
        assertNull(reloadedSku.getMarketPrice());
        assertEquals(List.of(), reloadedSku.getSpecificationValues());
    }

    private ProductCreateOwnReqVO createRequest(String code, String skuCode) {
        ProductCreateOwnReqVO request = new ProductCreateOwnReqVO();
        request.setCode(code).setStoreId(store.getId()).setCategoryId(category.getId()).setName("Product")
                .setMainImageUrl("main.jpg").setDescription("Description").setSort(0)
                .setSkus(List.of(new ProductSkuSaveReqVO().setCode(skuCode).setPrice(100L).setStock(1)
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0)));
        return request;
    }

    private ProductDO product(String code) {
        return new ProductDO().setMerchantId(merchant.getId()).setStoreId(store.getId())
                .setCategoryId(category.getId()).setCode(code).setName("Product")
                .setMainImageUrl("main.jpg").setDescription("Description").setAuditStatus(0)
                .setSaleStatus(0).setSort(0);
    }

    private ProductSkuDO sku(Long productId, String code) {
        return new ProductSkuDO().setProductId(productId).setMerchantId(merchant.getId()).setCode(code)
                .setPrice(100L).setStock(1).setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0);
    }
}
