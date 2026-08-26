package cn.iocoder.yudao.module.commerce.service.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.favorite.vo.ProductFavoriteRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.favorite.ProductFavoriteDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.favorite.ProductFavoriteMapper;
import cn.iocoder.yudao.module.commerce.service.catalog.PublicCatalogService;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.PRODUCT_FAVORITE_PRODUCT_NOT_AVAILABLE;

@Service
public class ProductFavoriteServiceImpl implements ProductFavoriteService {

    @Resource
    private ProductFavoriteMapper favoriteMapper;
    @Resource
    private PublicCatalogService catalogService;
    @Resource
    private MemberUserApi memberUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setFavorite(Long userId, Long productId, boolean favorite) {
        memberUserApi.validateActiveUser(userId);
        if (!favorite) {
            favoriteMapper.deleteByUserAndProduct(userId, productId);
            return;
        }
        if (catalogService.getVisibleSummary(productId, null) == null) {
            throw exception(PRODUCT_FAVORITE_PRODUCT_NOT_AVAILABLE);
        }
        if (favoriteMapper.selectByUserAndProduct(userId, productId) != null) {
            return;
        }
        try {
            favoriteMapper.insert(new ProductFavoriteDO().setMemberUserId(userId).setProductId(productId));
        } catch (DuplicateKeyException ignored) {
            // Concurrent duplicate requests converge on the same favorite row.
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long productId) {
        memberUserApi.validateActiveUser(userId);
        return favoriteMapper.selectByUserAndProduct(userId, productId) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductFavoriteRespVO> getFavoritePage(Long userId, PageParam pageParam) {
        memberUserApi.validateActiveUser(userId);
        PageResult<ProductFavoriteDO> page = favoriteMapper.selectPageByUserId(pageParam, userId);
        return new PageResult<>(page.getList().stream().map(favorite -> new ProductFavoriteRespVO()
                .setId(favorite.getId())
                .setProductId(favorite.getProductId())
                .setFavoriteTime(favorite.getCreateTime())
                .setProduct(catalogService.getVisibleSummary(favorite.getProductId(), null))).toList(), page.getTotal());
    }
}
