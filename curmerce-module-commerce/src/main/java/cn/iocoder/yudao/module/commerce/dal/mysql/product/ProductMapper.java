package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapperX<ProductDO> {

    default ProductDO selectByIdAndMerchantId(Long id, Long merchantId) {
        return selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getId, id)
                .eq(ProductDO::getMerchantId, merchantId));
    }

    default ProductDO selectByMerchantIdAndCode(Long merchantId, String code) {
        return selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getMerchantId, merchantId)
                .eq(ProductDO::getCode, code));
    }
}
