package cn.iocoder.yudao.module.commerce.dal.mysql.release;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseCommandDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommerceReleasePurchaseCommandMapper extends BaseMapperX<CommerceReleasePurchaseCommandDO> {

    default CommerceReleasePurchaseCommandDO selectByTicket(String ticket) {
        return selectOne(new LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                .eq(CommerceReleasePurchaseCommandDO::getTicket, ticket));
    }

    default CommerceReleasePurchaseCommandDO selectByTicketForUpdate(String ticket) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                .eq(CommerceReleasePurchaseCommandDO::getTicket, ticket));
    }

    default CommerceReleasePurchaseCommandDO selectByBuyerItemAndKey(Long buyerUserId, Long itemId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                .eq(CommerceReleasePurchaseCommandDO::getBuyerUserId, buyerUserId)
                .eq(CommerceReleasePurchaseCommandDO::getItemId, itemId)
                .eq(CommerceReleasePurchaseCommandDO::getIdempotencyKey, idempotencyKey));
    }

    default CommerceReleasePurchaseCommandDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                .eq(CommerceReleasePurchaseCommandDO::getId, id));
    }

    /** Finds commands that lost a consumer or have reached their retry time. */
    default List<CommerceReleasePurchaseCommandDO> selectRecoverable(LocalDateTime now, int limit) {
        int safeLimit = Math.max(1, Math.min(1000, limit));
        return selectList(new LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                .and(wrapper -> wrapper
                        .and(value -> value.eq(CommerceReleasePurchaseCommandDO::getStatus, 50)
                                .le(CommerceReleasePurchaseCommandDO::getRetryAt, now))
                        .or(value -> value.eq(CommerceReleasePurchaseCommandDO::getStatus, 20)
                                .le(CommerceReleasePurchaseCommandDO::getProcessingDeadline, now)))
                .orderByAsc(CommerceReleasePurchaseCommandDO::getId)
                .last("LIMIT " + safeLimit));
    }
}
