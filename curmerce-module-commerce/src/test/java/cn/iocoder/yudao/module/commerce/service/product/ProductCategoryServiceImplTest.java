package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryUpdateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryUpdateStatusReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductCategoryMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceImplTest {

    @Mock private ProductCategoryMapper categoryMapper;
    @Mock private ProductMapper productMapper;
    @Mock private FileApi fileApi;
    @InjectMocks private ProductCategoryServiceImpl service;

    @Test
    void create_startsDisabledAndValidatesParent() {
        when(categoryMapper.selectListOrderedForUpdate()).thenReturn(List.of(root(1L, CommonStatusEnum.ENABLE.getStatus())));
        ProductCategoryCreateReqVO request = new ProductCategoryCreateReqVO().setParentId(1L)
                .setCode("child_category").setName(" Child ").setSort(2);
        when(categoryMapper.insert((ProductCategoryDO) any(ProductCategoryDO.class))).thenAnswer(invocation -> {
            ProductCategoryDO inserted = invocation.getArgument(0);
            inserted.setId(2L);
            return 1;
        });

        assertEquals(2L, service.createCategory(request));
        verify(categoryMapper).insert((ProductCategoryDO) argThat((ProductCategoryDO category) ->
                category.getStatus().equals(CommonStatusEnum.DISABLE.getStatus())
                        && category.getParentId().equals(1L)
                        && category.getName().equals("Child")));
    }

    @Test
    void update_rejectsSelfAndDeepCycle() {
        ProductCategoryDO root = root(1L, CommonStatusEnum.ENABLE.getStatus());
        ProductCategoryDO child = new ProductCategoryDO().setId(2L).setParentId(1L).setCode("child")
                .setName("Child").setSort(0).setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(categoryMapper.selectListOrderedForUpdate()).thenReturn(List.of(root, child));

        ServiceException self = assertThrows(ServiceException.class, () -> service.updateCategory(
                new ProductCategoryUpdateReqVO().setId(1L).setParentId(1L).setName("Root")));
        assertEquals(PRODUCT_CATEGORY_PARENT_SELF.getCode(), self.getCode());

        ServiceException cycle = assertThrows(ServiceException.class, () -> service.updateCategory(
                new ProductCategoryUpdateReqVO().setId(1L).setParentId(2L).setName("Root")));
        assertEquals(PRODUCT_CATEGORY_PARENT_CYCLE.getCode(), cycle.getCode());
    }

    @Test
    void enable_requiresEnabledAncestors() {
        ProductCategoryDO parent = root(1L, CommonStatusEnum.DISABLE.getStatus());
        ProductCategoryDO child = new ProductCategoryDO().setId(2L).setParentId(1L).setCode("child")
                .setName("Child").setSort(0).setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(categoryMapper.selectListOrderedForUpdate()).thenReturn(List.of(parent, child));

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateCategoryStatus(
                new ProductCategoryUpdateStatusReqVO().setId(2L).setStatus(CommonStatusEnum.ENABLE.getStatus())));
        assertEquals(PRODUCT_CATEGORY_ANCESTOR_DISABLED.getCode(), error.getCode());
        verify(categoryMapper, never()).updateStatusExpected(any(), any(), any());
    }

    @Test
    void disable_rejectsEnabledDescendantAndOnSaleSubtree() {
        ProductCategoryDO parent = root(1L, CommonStatusEnum.ENABLE.getStatus());
        ProductCategoryDO child = new ProductCategoryDO().setId(2L).setParentId(1L).setCode("child")
                .setName("Child").setSort(0).setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(categoryMapper.selectListOrderedForUpdate()).thenReturn(List.of(parent, child));
        ServiceException enabledChild = assertThrows(ServiceException.class, () -> service.updateCategoryStatus(
                new ProductCategoryUpdateStatusReqVO().setId(1L).setStatus(CommonStatusEnum.DISABLE.getStatus())));
        assertEquals(PRODUCT_CATEGORY_ENABLED_DESCENDANT.getCode(), enabledChild.getCode());

        child.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(productMapper.countOnSaleByCategoryIds(any())).thenReturn(1L);
        ServiceException onSale = assertThrows(ServiceException.class, () -> service.updateCategoryStatus(
                new ProductCategoryUpdateStatusReqVO().setId(1L).setStatus(CommonStatusEnum.DISABLE.getStatus())));
        assertEquals(PRODUCT_CATEGORY_SUBTREE_PRODUCT_ON_SALE.getCode(), onSale.getCode());
    }

    @Test
    void tree_isSortedAndFailsClosedOnCycle() {
        ProductCategoryDO root = root(1L, CommonStatusEnum.ENABLE.getStatus()).setSort(5);
        ProductCategoryDO child = new ProductCategoryDO().setId(2L).setParentId(1L).setCode("child")
                .setName("Child").setSort(1).setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(categoryMapper.selectListOrdered()).thenReturn(List.of(root, child));
        var result = service.getCategoryTree();
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getChildren().get(0).getId());

        root.setParentId(2L);
        ServiceException error = assertThrows(ServiceException.class, service::getCategoryTree);
        assertEquals(PRODUCT_CATEGORY_TREE_INVALID.getCode(), error.getCode());
    }

    private ProductCategoryDO root(Long id, Integer status) {
        return new ProductCategoryDO().setId(id).setCode("root_" + id).setName("Root")
                .setSort(0).setStatus(status);
    }
}
