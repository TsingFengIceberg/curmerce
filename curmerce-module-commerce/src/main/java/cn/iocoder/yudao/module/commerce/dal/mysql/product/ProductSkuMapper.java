package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductSkuMapper extends BaseMapperX<ProductSkuDO> {

    default ProductSkuDO selectByMerchantIdAndCode(Long merchantId, String code) {
        return selectOne(new LambdaQueryWrapper<ProductSkuDO>()
                .eq(ProductSkuDO::getMerchantId, merchantId)
                .eq(ProductSkuDO::getCode, code));
    }

    default List<ProductSkuDO> selectListByProductIdAndMerchantId(Long productId, Long merchantId) {
        return selectList(new LambdaQueryWrapper<ProductSkuDO>()
                .eq(ProductSkuDO::getProductId, productId)
                .eq(ProductSkuDO::getMerchantId, merchantId)
                .orderByAsc(ProductSkuDO::getSort)
                .orderByAsc(ProductSkuDO::getId));
    }

    default List<ProductSkuDO> selectListByProductId(Long productId) {
        return selectList(new LambdaQueryWrapper<ProductSkuDO>()
                .eq(ProductSkuDO::getProductId, productId)
                .orderByAsc(ProductSkuDO::getSort)
                .orderByAsc(ProductSkuDO::getId));
    }

    default List<ProductSkuDO> selectListByProductIdForUpdate(Long productId) {
        return selectList(new LambdaQueryWrapper<ProductSkuDO>()
                .eq(ProductSkuDO::getProductId, productId)
                .orderByAsc(ProductSkuDO::getId)
                .last("FOR UPDATE"));
    }

    default List<ProductSkuDO> selectListByProductIdAndMerchantIdForUpdate(Long productId, Long merchantId) {
        return selectList(new LambdaQueryWrapper<ProductSkuDO>()
                .eq(ProductSkuDO::getProductId, productId)
                .eq(ProductSkuDO::getMerchantId, merchantId)
                .orderByAsc(ProductSkuDO::getId)
                .last("FOR UPDATE"));
    }

    default long countSellableByProductId(Long productId) {
        return selectCount(new LambdaQueryWrapper<ProductSkuDO>()
                .eq(ProductSkuDO::getProductId, productId)
                .eq(ProductSkuDO::getStatus, cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE.getStatus())
                .gt(ProductSkuDO::getStock, 0));
    }

    default int deleteByIdsAndOwnership(Collection<Long> ids, Long productId, Long merchantId) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductSkuDO>()
                .in(ProductSkuDO::getId, ids)
                .eq(ProductSkuDO::getProductId, productId)
                .eq(ProductSkuDO::getMerchantId, merchantId));
    }

    default int updateOwned(ProductSkuDO update) {
        LambdaUpdateWrapper<ProductSkuDO> wrapper = new LambdaUpdateWrapper<ProductSkuDO>()
                .eq(ProductSkuDO::getId, update.getId())
                .eq(ProductSkuDO::getProductId, update.getProductId())
                .eq(ProductSkuDO::getMerchantId, update.getMerchantId());
        return update(update, wrapper);
    }
}
