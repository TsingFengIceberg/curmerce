package cn.iocoder.yudao.module.commerce.dal.mysql.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface MerchantMapper extends BaseMapperX<MerchantDO> {
    default MerchantDO selectByCode(String code) { return selectOne(MerchantDO::getCode, code); }
    default MerchantDO selectByDefaultStoreCode(String code) { return selectOne(MerchantDO::getDefaultStoreCode, code); }
    default PageResult<MerchantDO> selectPage(MerchantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantDO>()
                .likeIfPresent(MerchantDO::getName, reqVO.getName())
                .eqIfPresent(MerchantDO::getCode, reqVO.getCode())
                .eqIfPresent(MerchantDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MerchantDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MerchantDO::getId));
    }
    default MerchantDO selectPendingForUpdate(Long id) {
        return selectOneForUpdate(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantDO>()
                .eq(MerchantDO::getId, id));
    }
    default int updateReview(Long id, Integer fromStatus, Integer toStatus, Long reviewerId,
                             LocalDateTime reviewTime, Long ownerUserId, String rejectReason) {
        LambdaUpdateWrapper<MerchantDO> wrapper = new LambdaUpdateWrapper<MerchantDO>()
                .eq(MerchantDO::getId, id).eq(MerchantDO::getStatus, fromStatus);
        MerchantDO update = new MerchantDO().setStatus(toStatus).setReviewerUserId(reviewerId)
                .setReviewTime(reviewTime).setOwnerUserId(ownerUserId).setRejectReason(rejectReason);
        return update(update, wrapper);
    }
}
