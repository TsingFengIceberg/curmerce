package cn.iocoder.yudao.module.commerce.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MerchantOperatorMapper extends BaseMapperX<MerchantOperatorDO> {
    default List<MerchantOperatorDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<MerchantOperatorDO>().eq(MerchantOperatorDO::getUserId, userId));
    }
    default MerchantOperatorDO selectByMerchantAndUser(Long merchantId, Long userId) {
        return selectOne(new LambdaQueryWrapper<MerchantOperatorDO>().eq(MerchantOperatorDO::getMerchantId, merchantId)
                .eq(MerchantOperatorDO::getUserId, userId));
    }
}
