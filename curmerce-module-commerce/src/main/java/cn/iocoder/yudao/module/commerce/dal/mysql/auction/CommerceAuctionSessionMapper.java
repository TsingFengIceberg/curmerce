package cn.iocoder.yudao.module.commerce.dal.mysql.auction;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionSessionDO;
import cn.iocoder.yudao.module.commerce.controller.admin.auction.vo.AuctionPageReqVO;
import cn.iocoder.yudao.module.commerce.enums.auction.AuctionStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommerceAuctionSessionMapper extends BaseMapperX<CommerceAuctionSessionDO> {
    default PageResult<CommerceAuctionSessionDO> selectPublicPage(cn.iocoder.yudao.framework.common.pojo.PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceAuctionSessionDO>()
                .in(CommerceAuctionSessionDO::getStatus, AuctionStatusEnum.SCHEDULED.getStatus(), AuctionStatusEnum.RUNNING.getStatus(),
                        AuctionStatusEnum.ENDED.getStatus(), AuctionStatusEnum.SETTLEMENT_FAILED.getStatus())
                .orderByAsc(CommerceAuctionSessionDO::getStatus).orderByAsc(CommerceAuctionSessionDO::getStartTime)
                .orderByAsc(CommerceAuctionSessionDO::getId));
    }
    default PageResult<CommerceAuctionSessionDO> selectOwnPage(AuctionPageReqVO req, Long merchantId) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceAuctionSessionDO>()
                .eq(CommerceAuctionSessionDO::getMerchantId, merchantId)
                .eqIfPresent(CommerceAuctionSessionDO::getStatus, req.getStatus())
                .likeIfPresent(CommerceAuctionSessionDO::getName, req.getName())
                .orderByDesc(CommerceAuctionSessionDO::getId));
    }
    default CommerceAuctionSessionDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommerceAuctionSessionDO>()
                .eq(CommerceAuctionSessionDO::getId, id));
    }
    default int updateStatus(Long id, Long merchantId, Integer expected, Integer target) {
        return update(new CommerceAuctionSessionDO().setStatus(target), new LambdaUpdateWrapper<CommerceAuctionSessionDO>()
                .eq(CommerceAuctionSessionDO::getId, id).eq(CommerceAuctionSessionDO::getMerchantId, merchantId)
                .eq(CommerceAuctionSessionDO::getStatus, expected));
    }

    default int promoteScheduled(LocalDateTime now) {
        return update(new CommerceAuctionSessionDO().setStatus(AuctionStatusEnum.RUNNING.getStatus()),
                new LambdaUpdateWrapper<CommerceAuctionSessionDO>()
                        .eq(CommerceAuctionSessionDO::getStatus, AuctionStatusEnum.SCHEDULED.getStatus())
                        .le(CommerceAuctionSessionDO::getStartTime, now)
                        .gt(CommerceAuctionSessionDO::getEndTime, now));
    }

    default List<CommerceAuctionSessionDO> selectExpiredForUpdate(LocalDateTime now, int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 1000));
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommerceAuctionSessionDO>()
                .in(CommerceAuctionSessionDO::getStatus, AuctionStatusEnum.SCHEDULED.getStatus(), AuctionStatusEnum.RUNNING.getStatus())
                .le(CommerceAuctionSessionDO::getEndTime, now).orderByAsc(CommerceAuctionSessionDO::getId)
                .last("LIMIT " + safeBatchSize + " FOR UPDATE"));
    }

    default List<CommerceAuctionSessionDO> selectSettledEndedForUpdate(int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 1000));
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommerceAuctionSessionDO>()
                .eq(CommerceAuctionSessionDO::getStatus, AuctionStatusEnum.ENDED.getStatus())
                .isNotNull(CommerceAuctionSessionDO::getSettlementOrderId)
                .orderByAsc(CommerceAuctionSessionDO::getId)
                .last("LIMIT " + safeBatchSize + " FOR UPDATE"));
    }

    default int markSettlementFailed(Long id, LocalDateTime failedTime, String reason) {
        return update(new CommerceAuctionSessionDO().setStatus(AuctionStatusEnum.SETTLEMENT_FAILED.getStatus())
                        .setSettlementFailedTime(failedTime).setSettlementFailureReason(reason),
                new LambdaUpdateWrapper<CommerceAuctionSessionDO>().eq(CommerceAuctionSessionDO::getId, id)
                        .eq(CommerceAuctionSessionDO::getStatus, AuctionStatusEnum.ENDED.getStatus()));
    }
}
