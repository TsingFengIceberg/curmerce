package cn.iocoder.yudao.module.commerce.service.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.favorite.vo.ProductFavoriteRespVO;

public interface ProductFavoriteService {

    void setFavorite(Long userId, Long productId, boolean favorite);

    boolean isFavorite(Long userId, Long productId);

    PageResult<ProductFavoriteRespVO> getFavoritePage(Long userId, PageParam pageParam);
}
