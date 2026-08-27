package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductCreateOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductIdReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductPageOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductRejectReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductCategoryMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.product.ProductAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSellerTypeEnum;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import cn.iocoder.yudao.framework.common.pojo.PageResult;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductMapper productMapper;
    @Mock private ProductSkuMapper skuMapper;
    @Mock private MerchantMapper merchantMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private ProductCategoryMapper categoryMapper;
    @Mock private ProductCategoryService categoryService;
    @Mock private MerchantAccessService merchantAccessService;
    @Mock private ProductOperationLogService operationLogService;
    @Mock private FileApi fileApi;
    @InjectMocks private ProductServiceImpl service;

    @Test
    void create_usesServerMerchantAndStartsDraftOffShelf() {
        MerchantAccessContext context = context(7L, 8L, CommonStatusEnum.ENABLE.getStatus());
        when(merchantAccessService.requireApprovedOwner()).thenReturn(context);
        when(categoryService.lockCategorySnapshot()).thenReturn(List.of(enabledCategory(3L)));
        when(productMapper.selectByMerchantIdAndCode(7L, "product_one")).thenReturn(null);
        when(productMapper.insert((ProductDO) any(ProductDO.class))).thenAnswer(invocation -> {
            ProductDO product = invocation.getArgument(0);
            product.setId(10L);
            return 1;
        });
        when(skuMapper.insert((ProductSkuDO) any(ProductSkuDO.class))).thenAnswer(invocation -> {
            ProductSkuDO sku = invocation.getArgument(0);
            sku.setId(11L);
            return 1;
        });

        ProductCreateOwnReqVO request = productRequest().setCode("product_one");
        assertEquals(10L, service.createOwnProduct(request));
        verify(productMapper).insert((ProductDO) argThat((ProductDO product) ->
                product.getMerchantId().equals(7L)
                        && product.getStoreId().equals(8L)
                        && product.getSellerType().equals(ProductSellerTypeEnum.MERCHANT.getType())
                        && product.getAuditStatus().equals(ProductAuditStatusEnum.DRAFT.getStatus())
                        && product.getSaleStatus().equals(ProductSaleStatusEnum.OFF_SHELF.getStatus())));
        verify(skuMapper).insert((ProductSkuDO) argThat((ProductSkuDO sku) ->
                sku.getMerchantId().equals(7L) && sku.getProductId().equals(10L)));
        verify(operationLogService).record(eq(10L), isNull(), eq(ProductOperationLogService.OPERATOR_MERCHANT),
                eq("CREATE"), isNull(), eq(0), isNull(), eq(0), anyString());
    }

    @Test
    void update_rejectsForeignProductAndDoesNotTouchChildren() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(context(7L, 8L, CommonStatusEnum.ENABLE.getStatus()));
        when(categoryService.lockCategorySnapshot()).thenReturn(List.of(enabledCategory(3L)));
        when(productMapper.selectByIdAndMerchantIdForUpdate(10L, 7L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateOwnProduct(updateRequest()));
        assertEquals(PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED.getCode(), error.getCode());
        verifyNoInteractions(skuMapper);
    }

    @Test
    void ownProductPage_includesSkusInListResponse() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(
                context(7L, 8L, CommonStatusEnum.ENABLE.getStatus()));
        ProductDO product = baseProduct();
        when(productMapper.selectPageOwn(any(ProductPageOwnReqVO.class), eq(7L)))
                .thenReturn(new PageResult<>(List.of(product), 1L));
        when(skuMapper.selectListByProductIdsAndMerchantId(List.of(10L), 7L))
                .thenReturn(List.of(validSku(11L), validSku(12L).setCode("sku_two")));

        PageResult<ProductAggregate> result = service.getOwnProductPage(new ProductPageOwnReqVO());

        assertEquals(1L, result.getTotal());
        assertEquals(2, result.getList().get(0).skus().size());
        verify(skuMapper).selectListByProductIdsAndMerchantId(List.of(10L), 7L);
    }

    @Test
    void submit_clearsReviewMetadataAndMovesDraftToPending() {
        ProductDO draft = baseProduct().setAuditStatus(ProductAuditStatusEnum.DRAFT.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus());
        when(merchantAccessService.requireApprovedOwner()).thenReturn(context(7L, 8L, CommonStatusEnum.ENABLE.getStatus()));
        when(categoryService.lockCategorySnapshot()).thenReturn(List.of(enabledCategory(3L)));
        when(productMapper.selectByIdAndMerchantIdForUpdate(10L, 7L)).thenReturn(draft);
        when(skuMapper.selectListByProductIdAndMerchantIdForUpdate(10L, 7L)).thenReturn(List.of(validSku(11L)));
        when(productMapper.updateAuditExpected(10L, 0, 1, null, null, null)).thenReturn(1);

        service.submitOwnProduct(10L);
        verify(productMapper).updateAuditExpected(10L, 0, 1, null, null, null);
        verify(operationLogService).record(eq(10L), isNull(), eq(ProductOperationLogService.OPERATOR_MERCHANT),
                eq("SUBMIT_REVIEW"), eq(0), eq(1), eq(0), eq(0), anyString());
    }

    @Test
    void list_requiresApprovedProductAndSellableSku() {
        ProductDO approved = baseProduct().setAuditStatus(ProductAuditStatusEnum.APPROVED.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus());
        when(merchantAccessService.requireApprovedOwner()).thenReturn(context(7L, 8L, CommonStatusEnum.ENABLE.getStatus()));
        when(categoryService.lockCategorySnapshot()).thenReturn(List.of(enabledCategory(3L)));
        when(productMapper.selectByIdAndMerchantIdForUpdate(10L, 7L)).thenReturn(approved);
        when(skuMapper.selectListByProductIdAndMerchantIdForUpdate(10L, 7L)).thenReturn(List.of(
                validSku(11L).setStock(0)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.listOwnProduct(10L));
        assertEquals(PRODUCT_NO_SELLABLE_SKU.getCode(), error.getCode());
        verify(productMapper, never()).updateSaleExpected(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void approve_recordsReviewerAndMovesPendingToApproved() {
        ProductDO pending = baseProduct().setAuditStatus(ProductAuditStatusEnum.PENDING.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus());
        when(productMapper.selectByIdForUpdate(10L)).thenReturn(pending);
        when(productMapper.updateAuditExpected(eq(10L), eq(1), eq(2), eq(99L), any(), isNull())).thenReturn(1);
        service.approveProduct(10L, 99L);
        verify(productMapper).updateAuditExpected(eq(10L), eq(1), eq(2), eq(99L), any(), isNull());
    }

    @Test
    void reject_requiresTrimmedReason() {
        ProductDO pending = baseProduct().setAuditStatus(ProductAuditStatusEnum.PENDING.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus());
        when(productMapper.selectByIdForUpdate(10L)).thenReturn(pending);
        ServiceException error = assertThrows(ServiceException.class, () -> service.rejectProduct(
                new ProductRejectReqVO().setId(10L).setReason("   "), 99L));
        assertEquals(PRODUCT_AUDIT_STATE_INVALID.getCode(), error.getCode());
        verify(productMapper, never()).updateAuditExpected(anyLong(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void delist_rejectsAlreadyOffShelf() {
        ProductDO approved = baseProduct().setAuditStatus(ProductAuditStatusEnum.APPROVED.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus());
        when(merchantAccessService.requireApprovedOwner()).thenReturn(context(7L, 8L, CommonStatusEnum.ENABLE.getStatus()));
        when(productMapper.selectByIdAndMerchantIdForUpdate(10L, 7L)).thenReturn(approved);
        ServiceException error = assertThrows(ServiceException.class, () -> service.delistOwnProduct(10L));
        assertEquals(PRODUCT_SALE_STATE_INVALID.getCode(), error.getCode());
    }

    @Test
    void create_rejectsSkuStatusOutsideCommonStatus() {
        when(merchantAccessService.requireApprovedOwner()).thenReturn(
                context(7L, 8L, CommonStatusEnum.ENABLE.getStatus()));
        when(categoryService.lockCategorySnapshot()).thenReturn(List.of(enabledCategory(3L)));

        ProductCreateOwnReqVO request = productRequest().setCode("product_status_invalid");
        request.getSkus().get(0).setStatus(7);

        ServiceException error = assertThrows(ServiceException.class, () -> service.createOwnProduct(request));
        assertEquals(PRODUCT_SKU_STATUS_INVALID.getCode(), error.getCode());
        verifyNoInteractions(productMapper, skuMapper);
    }

    private ProductCreateOwnReqVO productRequest() {
        return (ProductCreateOwnReqVO) new ProductCreateOwnReqVO().setStoreId(8L).setCategoryId(3L)
                .setName("Product").setMainImageUrl("main.jpg").setDescription("Description").setSort(0)
                .setSkus(List.of(new ProductSkuSaveReqVO().setCode("sku_one").setPrice(100L).setStock(2)
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0)));
    }

    private ProductUpdateOwnReqVO updateRequest() {
        ProductUpdateOwnReqVO request = new ProductUpdateOwnReqVO();
        request.setId(10L).setStoreId(8L).setCategoryId(3L).setName("Product")
                .setMainImageUrl("main.jpg").setDescription("Description").setSort(0)
                .setSkus(List.of(new ProductSkuSaveReqVO().setCode("sku_one").setPrice(100L).setStock(2)
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0)));
        return request;
    }

    private ProductDO baseProduct() {
        return new ProductDO().setId(10L).setMerchantId(7L).setStoreId(8L).setCategoryId(3L)
                .setCode("product_one").setName("Product").setMainImageUrl("main.jpg")
                .setDescription("Description").setSort(0);
    }

    private ProductSkuDO validSku(Long id) {
        return new ProductSkuDO().setId(id).setProductId(10L).setMerchantId(7L).setCode("sku_one")
                .setPrice(100L).setStock(2).setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0);
    }

    private ProductCategoryDO enabledCategory(Long id) {
        return new ProductCategoryDO().setId(id).setCode("category").setName("Category")
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(0);
    }

    private MerchantAccessContext context(Long merchantId, Long storeId, Integer storeStatus) {
        return new MerchantAccessContext(new MerchantDO().setId(merchantId),
                new StoreDO().setId(storeId).setMerchantId(merchantId).setStatus(storeStatus));
    }
}
