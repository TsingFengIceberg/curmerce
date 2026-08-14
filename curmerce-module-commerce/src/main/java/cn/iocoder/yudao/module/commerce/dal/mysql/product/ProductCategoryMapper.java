package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

    default List<ProductCategoryDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .orderByAsc(ProductCategoryDO::getSort)
                .orderByAsc(ProductCategoryDO::getId));
    }

    default List<ProductCategoryDO> selectListOrderedForUpdate() {
        return selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .orderByAsc(ProductCategoryDO::getId)
                .last("FOR UPDATE"));
    }

    default int updateStatusExpected(Long id, Integer expectedStatus, Integer targetStatus) {
        return update(new ProductCategoryDO().setStatus(targetStatus),
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProductCategoryDO>()
                        .eq(ProductCategoryDO::getId, id)
                        .eq(ProductCategoryDO::getStatus, expectedStatus));
    }

    default int updateDetails(ProductCategoryDO update) {
        LambdaUpdateWrapper<ProductCategoryDO> wrapper = new LambdaUpdateWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getId, update.getId());
        return update(update, wrapper);
    }
}
