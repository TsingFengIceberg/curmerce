package cn.iocoder.yudao.module.commerce.dal.mysql.release;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseItemDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommerceReleaseItemMapper extends BaseMapperX<CommerceReleaseItemDO> {
    default List<CommerceReleaseItemDO> selectByCampaignId(Long campaignId) {
        return selectList(new LambdaQueryWrapper<CommerceReleaseItemDO>().eq(CommerceReleaseItemDO::getCampaignId, campaignId)
                .orderByAsc(CommerceReleaseItemDO::getId));
    }
    default CommerceReleaseItemDO selectByCampaignAndSku(Long campaignId, Long skuId) {
        return selectOne(new LambdaQueryWrapper<CommerceReleaseItemDO>().eq(CommerceReleaseItemDO::getCampaignId, campaignId)
                .eq(CommerceReleaseItemDO::getSkuId, skuId));
    }
    default CommerceReleaseItemDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<CommerceReleaseItemDO>().eq(CommerceReleaseItemDO::getId, id));
    }
    default int deleteByCampaignId(Long campaignId) {
        return delete(new LambdaQueryWrapper<CommerceReleaseItemDO>()
                .eq(CommerceReleaseItemDO::getCampaignId, campaignId));
    }
    default int updateSold(Long id, int quantity) {
        return update(new CommerceReleaseItemDO().setSoldCount(quantity),
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CommerceReleaseItemDO>()
                        .eq(CommerceReleaseItemDO::getId, id)
                        .setSql("sold_count = sold_count + " + quantity));
    }
    default int updateInventory(Long id, int quantity) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CommerceReleaseItemDO>()
                .eq(CommerceReleaseItemDO::getId, id).ge(CommerceReleaseItemDO::getStock, quantity)
                .setSql("stock = stock - " + quantity + ", sold_count = sold_count + " + quantity));
    }

    default int restoreInventory(Long id, int quantity) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CommerceReleaseItemDO>()
                .eq(CommerceReleaseItemDO::getId, id).ge(CommerceReleaseItemDO::getSoldCount, quantity)
                .setSql("stock = stock + " + quantity + ", sold_count = sold_count - " + quantity));
    }
}
