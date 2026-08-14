package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductCategoryMapper extends BaseMapperX<ProductCategoryDO> {

    default ProductCategoryDO selectByCode(String code) {
        return selectOne(ProductCategoryDO::getCode, code);
    }

    default List<ProductCategoryDO> selectListByParentIdAndStatus(Long parentId, Integer status) {
        LambdaQueryWrapper<ProductCategoryDO> wrapper = new LambdaQueryWrapper<>();
        if (parentId == null) {
            wrapper.isNull(ProductCategoryDO::getParentId);
        } else {
            wrapper.eq(ProductCategoryDO::getParentId, parentId);
        }
        return selectList(wrapper.eq(ProductCategoryDO::getStatus, status)
                .orderByAsc(ProductCategoryDO::getSort)
                .orderByAsc(ProductCategoryDO::getId));
    }
}
