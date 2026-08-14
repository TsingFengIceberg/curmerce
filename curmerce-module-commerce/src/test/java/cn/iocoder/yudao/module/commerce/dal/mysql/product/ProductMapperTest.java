package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import org.junit.jupiter.api.Test;

import jakarta.annotation.Resource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductMapperTest extends BaseDbUnitTest {

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
    void productAndSkuJsonRoundTripAndQueriesRemainMerchantScoped() {
        MerchantStore merchantA = createMerchantStore("merchant_a");
        MerchantStore merchantB = createMerchantStore("merchant_b");
        ProductCategoryDO category = createCategory("category_food");

        ProductDO productA = validProduct(merchantA, category, "product_shared_code");
        productA.setImageUrls(List.of("https://img.example/a-1.jpg", "https://img.example/a-2.jpg"));
        productMapper.insert(productA);

        ProductDO productB = validProduct(merchantB, category, "product_shared_code");
        productMapper.insert(productB);

        ProductSkuDO skuSlow = validSku(productA, merchantA, "sku_slow", 2, 1200L, 3);
        skuSlow.setSpecificationValues(List.of(new ProductSkuDO.SpecificationValue("颜色", "蓝")));
        skuMapper.insert(skuSlow);
        ProductSkuDO skuFast = validSku(productA, merchantA, "sku_fast", 1, 1100L, 5);
        skuMapper.insert(skuFast);

        ProductDO reloaded = productMapper.selectById(productA.getId());
        assertEquals(productA.getImageUrls(), reloaded.getImageUrls());
        assertEquals(productA.getId(), productMapper.selectByMerchantIdAndCode(
                merchantA.merchant().getId(), "product_shared_code").getId());
        assertNull(productMapper.selectByIdAndMerchantId(productA.getId(), merchantB.merchant().getId()));
        assertEquals(productB.getId(), productMapper.selectByIdAndMerchantId(
                productB.getId(), merchantB.merchant().getId()).getId());

        ProductSkuDO reloadedSku = skuMapper.selectByMerchantIdAndCode(merchantA.merchant().getId(), "sku_slow");
        assertEquals(skuSlow.getSpecificationValues(), reloadedSku.getSpecificationValues());
        assertEquals(List.of(skuFast.getId(), skuSlow.getId()),
                skuMapper.selectListByProductIdAndMerchantId(productA.getId(), merchantA.merchant().getId())
                        .stream().map(ProductSkuDO::getId).toList());
    }

    private ProductCategoryDO createCategory(String code) {
        ProductCategoryDO category = new ProductCategoryDO().setCode(code).setName("Food")
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
                .setCategoryId(category.getId()).setCode(code).setName("Product")
                .setMainImageUrl("https://img.example/main.jpg").setDescription("Description")
                .setAuditStatus(0).setSaleStatus(0).setSort(0);
    }

    private ProductSkuDO validSku(ProductDO product, MerchantStore merchant, String code, int sort,
                                  long price, int stock) {
        return new ProductSkuDO().setProductId(product.getId()).setMerchantId(merchant.merchant().getId())
                .setCode(code).setPrice(price).setStock(stock).setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setSort(sort);
    }

    private record MerchantStore(MerchantDO merchant, StoreDO store) {
    }
}
