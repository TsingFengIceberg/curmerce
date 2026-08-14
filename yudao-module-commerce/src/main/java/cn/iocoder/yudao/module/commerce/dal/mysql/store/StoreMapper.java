package cn.iocoder.yudao.module.commerce.dal.mysql.store;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StoreMapper extends BaseMapperX<StoreDO> {
    default StoreDO selectByMerchantId(Long merchantId) {
        return selectOne(new LambdaQueryWrapper<StoreDO>().eq(StoreDO::getMerchantId, merchantId));
    }
    default StoreDO selectByCode(String code) { return selectOne(StoreDO::getCode, code); }
    default int updateOwned(Long id, Long merchantId, StoreDO update) {
        return update(update, new LambdaUpdateWrapper<StoreDO>().eq(StoreDO::getId, id)
                .eq(StoreDO::getMerchantId, merchantId));
    }
}
