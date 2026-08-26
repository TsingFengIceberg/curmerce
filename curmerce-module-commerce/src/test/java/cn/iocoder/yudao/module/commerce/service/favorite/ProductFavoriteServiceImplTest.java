package cn.iocoder.yudao.module.commerce.service.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.PublicProductSummaryRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.favorite.ProductFavoriteDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.favorite.ProductFavoriteMapper;
import cn.iocoder.yudao.module.commerce.service.catalog.PublicCatalogService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.PRODUCT_FAVORITE_PRODUCT_NOT_AVAILABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFavoriteServiceImplTest {

    @Mock
    private ProductFavoriteMapper favoriteMapper;
    @Mock
    private PublicCatalogService catalogService;
    @Mock
    private MemberUserApi memberUserApi;
    @InjectMocks
    private ProductFavoriteServiceImpl service;

    @Test
    void setFavorite_addsVisibleProduct() {
        when(catalogService.getVisibleSummary(20L, null)).thenReturn(new PublicProductSummaryRespVO().setId(20L));

        service.setFavorite(7L, 20L, true);

        verify(memberUserApi).validateActiveUser(7L);
        verify(favoriteMapper).insert(any(ProductFavoriteDO.class));
    }

    @Test
    void setFavorite_isIdempotentWhenFavoriteAlreadyExists() {
        when(catalogService.getVisibleSummary(20L, null)).thenReturn(new PublicProductSummaryRespVO().setId(20L));
        when(favoriteMapper.selectByUserAndProduct(7L, 20L))
                .thenReturn(new ProductFavoriteDO().setId(30L).setMemberUserId(7L).setProductId(20L));

        service.setFavorite(7L, 20L, true);

        verify(favoriteMapper, never()).insert(any(ProductFavoriteDO.class));
    }

    @Test
    void setFavorite_concurrentDuplicateConvergesSafely() {
        when(catalogService.getVisibleSummary(20L, null)).thenReturn(new PublicProductSummaryRespVO().setId(20L));
        when(favoriteMapper.insert(any(ProductFavoriteDO.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertDoesNotThrow(() -> service.setFavorite(7L, 20L, true));
    }

    @Test
    void setFavorite_rejectsUnavailableProduct() {
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.setFavorite(7L, 20L, true));

        assertEquals(PRODUCT_FAVORITE_PRODUCT_NOT_AVAILABLE.getCode(), error.getCode());
        verify(favoriteMapper, never()).insert(any(ProductFavoriteDO.class));
    }

    @Test
    void setFavorite_removalIsIdempotentWithoutVisibilityCheck() {
        service.setFavorite(7L, 20L, false);

        verify(favoriteMapper).deleteByUserAndProduct(7L, 20L);
        verify(catalogService, never()).getVisibleSummary(any(), any());
    }

    @Test
    void isFavorite_returnsStoredStatus() {
        when(favoriteMapper.selectByUserAndProduct(7L, 20L)).thenReturn(new ProductFavoriteDO().setId(30L));

        assertTrue(service.isFavorite(7L, 20L));
        assertFalse(service.isFavorite(7L, 21L));
    }

    @Test
    void getFavoritePage_preservesUnavailableEntriesSoTheyCanBeRemoved() {
        PageParam pageParam = new PageParam().setPageNo(1).setPageSize(10);
        LocalDateTime favoriteTime = LocalDateTime.of(2026, 8, 26, 12, 0);
        when(favoriteMapper.selectPageByUserId(pageParam, 7L)).thenReturn(new PageResult<>(List.of(
                new ProductFavoriteDO().setId(30L).setProductId(20L).setCreateTime(favoriteTime),
                new ProductFavoriteDO().setId(31L).setProductId(21L).setCreateTime(favoriteTime)), 2L));
        when(catalogService.getVisibleSummary(20L, null)).thenReturn(new PublicProductSummaryRespVO().setId(20L));

        var response = service.getFavoritePage(7L, pageParam);

        assertEquals(2L, response.getTotal());
        assertEquals(2, response.getList().size());
        assertEquals(20L, response.getList().get(0).getProduct().getId());
        assertEquals(21L, response.getList().get(1).getProductId());
        assertEquals(null, response.getList().get(1).getProduct());
    }
}
