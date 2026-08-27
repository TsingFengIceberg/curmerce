package cn.iocoder.yudao.module.commerce.service.personal;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.PersonalListingCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.PersonalListingUpdateReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductCategoryMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductSkuMapper;
import cn.iocoder.yudao.module.commerce.enums.product.ProductAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSellerTypeEnum;
import cn.iocoder.yudao.module.commerce.service.product.ProductOperationLogService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.PERSONAL_LISTING_NOT_FOUND;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.PRODUCT_CATEGORY_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalListingServiceImplTest {

    @Mock private MemberUserApi memberUserApi;
    @Mock private ProductCategoryMapper categoryMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductSkuMapper skuMapper;
    @Mock private ProductOperationLogService operationLogService;
    @Mock private FileApi fileApi;
    @InjectMocks private PersonalListingServiceImpl service;

    @Test
    void create_startsPersonalDraftWithSingleStockAndNoMerchantOwner() {
        when(categoryMapper.selectById(3L)).thenReturn(enabledCategory());
        when(productMapper.insert(any(ProductDO.class))).thenAnswer(invocation -> {
            invocation.<ProductDO>getArgument(0).setId(10L);
            return 1;
        });

        Long id = service.create(101L, createRequest());

        assertEquals(10L, id);
        verify(productMapper).insert(argThat((ProductDO product) ->
                ProductSellerTypeEnum.PERSONAL.getType().equals(product.getSellerType())
                        && Long.valueOf(101L).equals(product.getSellerUserId())
                        && product.getMerchantId() == null && product.getStoreId() == null
                        && "九成新".equals(product.getCondition())));
        verify(skuMapper).insert(argThat((ProductSkuDO sku) -> sku.getProductId().equals(10L)
                && sku.getMerchantId() == null && sku.getStock().equals(1)));
        verify(operationLogService).record(eq(10L), eq(101L), eq(ProductOperationLogService.OPERATOR_PERSONAL),
                eq("CREATE"), isNull(), eq(0), isNull(), eq(0), anyString());
    }

    @Test
    void update_rejectsForeignListingBeforeReadingSku() {
        when(productMapper.selectPersonalByIdForUpdate(10L, 101L)).thenReturn(null);

        PersonalListingUpdateReqVO request = new PersonalListingUpdateReqVO();
        request.setId(10L);
        request.setCategoryId(3L);
        request.setName("Updated");
        request.setCondition("八成新");
        request.setMainImageUrl("main.jpg");
        request.setDescription("Description");
        request.setPrice(100L);
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.update(101L, request));

        assertEquals(PERSONAL_LISTING_NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(skuMapper);
    }

    @Test
    void submit_allowsDraftOrRejectedOnlyAndClearsReviewMetadata() {
        ProductDO rejected = baseProduct().setAuditStatus(ProductAuditStatusEnum.REJECTED.getStatus());
        when(categoryMapper.selectById(3L)).thenReturn(enabledCategory());
        when(productMapper.selectPersonalByIdForUpdate(10L, 101L)).thenReturn(rejected);
        when(skuMapper.selectListByProductIdForUpdate(10L)).thenReturn(List.of(personalSku()));
        when(productMapper.updateAuditExpected(10L, 3, 1, null, null, null)).thenReturn(1);

        service.submit(101L, 10L);

        verify(productMapper).updateAuditExpected(10L, 3, 1, null, null, null);
        verify(operationLogService).record(eq(10L), eq(101L), eq(ProductOperationLogService.OPERATOR_PERSONAL),
                eq("SUBMIT_REVIEW"), eq(3), eq(1), eq(0), eq(0), anyString());
    }

    @Test
    void submit_rejectsWhenAnAncestorCategoryIsDisabled() {
        ProductDO draft = baseProduct().setAuditStatus(ProductAuditStatusEnum.DRAFT.getStatus());
        when(productMapper.selectPersonalByIdForUpdate(10L, 101L)).thenReturn(draft);
        when(categoryMapper.selectById(3L)).thenReturn(enabledCategory().setParentId(2L));
        when(categoryMapper.selectById(2L)).thenReturn(new ProductCategoryDO().setId(2L)
                .setStatus(CommonStatusEnum.DISABLE.getStatus()));

        ServiceException error = assertThrows(ServiceException.class, () -> service.submit(101L, 10L));

        assertEquals(PRODUCT_CATEGORY_DISABLED.getCode(), error.getCode());
        verifyNoInteractions(skuMapper);
        verify(productMapper, never()).updateAuditExpected(anyLong(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void listAndDelistUseConditionalStateUpdates() {
        ProductDO approvedOffShelf = baseProduct().setAuditStatus(ProductAuditStatusEnum.APPROVED.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus());
        when(productMapper.selectPersonalByIdForUpdate(10L, 101L)).thenReturn(approvedOffShelf);
        when(skuMapper.selectListByProductIdForUpdate(10L)).thenReturn(List.of(personalSku()));
        when(productMapper.updatePersonalSaleExpected(10L, 101L, 0, 1)).thenReturn(1);

        service.list(101L, 10L);

        verify(productMapper).updatePersonalSaleExpected(10L, 101L, 0, 1);

        ProductDO approvedOnSale = baseProduct().setAuditStatus(ProductAuditStatusEnum.APPROVED.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.ON_SALE.getStatus());
        when(productMapper.selectPersonalByIdForUpdate(10L, 101L)).thenReturn(approvedOnSale);
        when(productMapper.updatePersonalSaleExpected(10L, 101L, 1, 0)).thenReturn(1);

        service.delist(101L, 10L);

        verify(productMapper).updatePersonalSaleExpected(10L, 101L, 1, 0);
    }

    private PersonalListingCreateReqVO createRequest() {
        return new PersonalListingCreateReqVO().setCategoryId(3L).setName("旧相机")
                .setCondition("九成新").setMainImageUrl("main.jpg").setDescription("Description")
                .setPrice(100L);
    }

    private ProductDO baseProduct() {
        return new ProductDO().setId(10L).setSellerType(ProductSellerTypeEnum.PERSONAL.getType())
                .setSellerUserId(101L).setCategoryId(3L).setName("旧相机")
                .setCondition("九成新").setMainImageUrl("main.jpg").setDescription("Description")
                .setAuditStatus(ProductAuditStatusEnum.DRAFT.getStatus())
                .setSaleStatus(ProductSaleStatusEnum.OFF_SHELF.getStatus());
    }

    private ProductSkuDO personalSku() {
        return new ProductSkuDO().setId(11L).setProductId(10L).setMerchantId(null)
                .setPrice(100L).setStock(1).setStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    private ProductCategoryDO enabledCategory() {
        return new ProductCategoryDO().setId(3L).setStatus(CommonStatusEnum.ENABLE.getStatus());
    }
}
