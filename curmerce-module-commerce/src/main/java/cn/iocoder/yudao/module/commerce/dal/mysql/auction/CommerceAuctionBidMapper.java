package cn.iocoder.yudao.module.commerce.dal.mysql.auction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionBidDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommerceAuctionBidMapper extends BaseMapperX<CommerceAuctionBidDO> {
    default CommerceAuctionBidDO selectBySessionAndKey(Long sessionId, String key) {
        return selectOne(new LambdaQueryWrapper<CommerceAuctionBidDO>().eq(CommerceAuctionBidDO::getSessionId, sessionId)
                .eq(CommerceAuctionBidDO::getIdempotencyKey, key));
    }
    default CommerceAuctionBidDO selectHighest(Long sessionId) {
        return selectOne(new LambdaQueryWrapper<CommerceAuctionBidDO>().eq(CommerceAuctionBidDO::getSessionId, sessionId)
                .orderByDesc(CommerceAuctionBidDO::getAmount).orderByAsc(CommerceAuctionBidDO::getId).last("LIMIT 1"));
    }
    default PageResult<CommerceAuctionBidDO> selectPageBySession(PageParam pageParam, Long sessionId) {
        return selectPage(pageParam, new LambdaQueryWrapper<CommerceAuctionBidDO>()
                .eq(CommerceAuctionBidDO::getSessionId, sessionId)
                .orderByDesc(CommerceAuctionBidDO::getId));
    }
    default Long selectCountBySession(Long sessionId) {
        return selectCount(new LambdaQueryWrapper<CommerceAuctionBidDO>()
                .eq(CommerceAuctionBidDO::getSessionId, sessionId));
    }
}
