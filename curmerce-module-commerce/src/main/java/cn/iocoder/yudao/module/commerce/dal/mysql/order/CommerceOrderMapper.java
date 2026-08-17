package cn.iocoder.yudao.module.commerce.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommerceOrderMapper extends BaseMapperX<CommerceOrderDO> {
    default CommerceOrderDO selectByUserAndIdempotencyKey(Long userId, String key) {
        return selectOne(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getMemberUserId, userId)
                .eq(CommerceOrderDO::getIdempotencyKey, key));
    }
    default CommerceOrderDO selectOwned(Long userId, Long id) {
        return selectOne(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                .eq(CommerceOrderDO::getMemberUserId, userId));
    }
    default PageResult<CommerceOrderDO> selectPageOwned(Long userId, PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceOrderDO>()
                .eq(CommerceOrderDO::getMemberUserId, userId)
                .orderByDesc(CommerceOrderDO::getId));
    }
}
