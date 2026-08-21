package cn.iocoder.yudao.module.commerce.dal.mysql.release;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseDO;
import cn.iocoder.yudao.module.commerce.enums.release.ReleasePurchaseStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommerceReleasePurchaseMapper extends BaseMapperX<CommerceReleasePurchaseDO> {
    default CommerceReleasePurchaseDO selectByBuyerAndItem(Long buyerId, Long itemId) {
        return selectOne(new LambdaQueryWrapper<CommerceReleasePurchaseDO>().eq(CommerceReleasePurchaseDO::getBuyerUserId, buyerId)
                .eq(CommerceReleasePurchaseDO::getItemId, itemId)
                .in(CommerceReleasePurchaseDO::getStatus, ReleasePurchaseStatusEnum.PENDING.getStatus(), ReleasePurchaseStatusEnum.PAID.getStatus()));
    }

    default CommerceReleasePurchaseDO selectByOrderIdForUpdate(Long orderId) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceReleasePurchaseDO>()
                .eq(CommerceReleasePurchaseDO::getOrderId, orderId));
    }

    default int markPaidByOrderId(Long orderId) {
        return update(new CommerceReleasePurchaseDO().setStatus(ReleasePurchaseStatusEnum.PAID.getStatus()),
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CommerceReleasePurchaseDO>()
                        .eq(CommerceReleasePurchaseDO::getOrderId, orderId)
                        .eq(CommerceReleasePurchaseDO::getStatus, ReleasePurchaseStatusEnum.PENDING.getStatus()));
    }

    default int markCanceled(Long id) {
        return update(new CommerceReleasePurchaseDO().setStatus(ReleasePurchaseStatusEnum.CANCELED.getStatus()),
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CommerceReleasePurchaseDO>()
                        .eq(CommerceReleasePurchaseDO::getId, id)
                        .eq(CommerceReleasePurchaseDO::getStatus, ReleasePurchaseStatusEnum.PENDING.getStatus()));
    }
}
