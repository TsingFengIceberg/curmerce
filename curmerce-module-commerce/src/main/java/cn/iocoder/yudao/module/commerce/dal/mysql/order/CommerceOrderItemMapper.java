package cn.iocoder.yudao.module.commerce.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderItemDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CommerceOrderItemMapper extends BaseMapperX<CommerceOrderItemDO> {
    default List<CommerceOrderItemDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapper<CommerceOrderItemDO>().eq(CommerceOrderItemDO::getOrderId, orderId)
                .orderByAsc(CommerceOrderItemDO::getId));
    }
}
