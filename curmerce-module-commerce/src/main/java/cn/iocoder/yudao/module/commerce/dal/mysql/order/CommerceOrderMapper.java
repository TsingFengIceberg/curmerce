package cn.iocoder.yudao.module.commerce.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.order.CommerceOrderDO;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    default CommerceOrderDO selectOwnedForUpdate(Long userId, Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                .eq(CommerceOrderDO::getMemberUserId, userId));
    }
    default CommerceOrderDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id));
    }
    default int markPaid(Long id) {
        return update(new CommerceOrderDO().setStatus(OrderStatusEnum.PAID_PENDING_SHIPMENT.getStatus()),
                new LambdaUpdateWrapper<CommerceOrderDO>().eq(CommerceOrderDO::getId, id)
                        .eq(CommerceOrderDO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getStatus()));
    }
    default PageResult<CommerceOrderDO> selectPageOwned(Long userId, PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceOrderDO>()
                .eq(CommerceOrderDO::getMemberUserId, userId)
                .orderByDesc(CommerceOrderDO::getId));
    }
}
