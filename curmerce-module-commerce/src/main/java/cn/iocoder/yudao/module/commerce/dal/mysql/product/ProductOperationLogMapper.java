package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductOperationLogDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductOperationLogMapper extends BaseMapperX<ProductOperationLogDO> {

    default PageResult<ProductOperationLogDO> selectPageByProductId(Long productId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapper<ProductOperationLogDO>()
                .eq(ProductOperationLogDO::getProductId, productId)
                .orderByDesc(ProductOperationLogDO::getId));
    }
}
