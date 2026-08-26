package cn.iocoder.yudao.module.commerce.dal.mysql.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.favorite.ProductFavoriteDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductFavoriteMapper extends BaseMapperX<ProductFavoriteDO> {

    default ProductFavoriteDO selectByUserAndProduct(Long userId, Long productId) {
        return selectOne(new LambdaQueryWrapper<ProductFavoriteDO>()
                .eq(ProductFavoriteDO::getMemberUserId, userId)
                .eq(ProductFavoriteDO::getProductId, productId));
    }

    default PageResult<ProductFavoriteDO> selectPageByUserId(PageParam pageParam, Long userId) {
        return selectPage(pageParam, new LambdaQueryWrapper<ProductFavoriteDO>()
                .eq(ProductFavoriteDO::getMemberUserId, userId)
                .orderByDesc(ProductFavoriteDO::getId));
    }

    default int deleteByUserAndProduct(Long userId, Long productId) {
        return delete(new LambdaQueryWrapper<ProductFavoriteDO>()
                .eq(ProductFavoriteDO::getMemberUserId, userId)
                .eq(ProductFavoriteDO::getProductId, productId));
    }
}
