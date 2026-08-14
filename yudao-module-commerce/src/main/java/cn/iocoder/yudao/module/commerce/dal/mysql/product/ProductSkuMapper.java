package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

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
}
