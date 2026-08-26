package cn.iocoder.yudao.module.commerce.dal.mysql.release;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseCampaignDO;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleasePageReqVO;
import cn.iocoder.yudao.module.commerce.enums.release.ReleaseStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;

@Mapper
public interface CommerceReleaseCampaignMapper extends BaseMapperX<CommerceReleaseCampaignDO> {
    default PageResult<CommerceReleaseCampaignDO> selectPublicPage(cn.iocoder.yudao.framework.common.pojo.PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceReleaseCampaignDO>()
                .in(CommerceReleaseCampaignDO::getStatus, ReleaseStatusEnum.SCHEDULED.getStatus(), ReleaseStatusEnum.RUNNING.getStatus())
                .orderByAsc(CommerceReleaseCampaignDO::getStartTime).orderByAsc(CommerceReleaseCampaignDO::getId));
    }
    default PageResult<CommerceReleaseCampaignDO> selectOwnPage(ReleasePageReqVO req, Long merchantId) {
        return selectPage(req, new LambdaQueryWrapperX<CommerceReleaseCampaignDO>()
                .eq(CommerceReleaseCampaignDO::getMerchantId, merchantId)
                .eqIfPresent(CommerceReleaseCampaignDO::getStatus, req.getStatus())
                .likeIfPresent(CommerceReleaseCampaignDO::getName, req.getName())
                .orderByDesc(CommerceReleaseCampaignDO::getId));
    }
    default CommerceReleaseCampaignDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommerceReleaseCampaignDO>()
                .eq(CommerceReleaseCampaignDO::getId, id));
    }
    default int updateStatus(Long id, Long merchantId, Integer expected, Integer target) {
        return update(new CommerceReleaseCampaignDO().setStatus(target), new LambdaUpdateWrapper<CommerceReleaseCampaignDO>()
                .eq(CommerceReleaseCampaignDO::getId, id).eq(CommerceReleaseCampaignDO::getMerchantId, merchantId)
                .eq(CommerceReleaseCampaignDO::getStatus, expected));
    }

    default int promoteScheduled(LocalDateTime now) {
        return update(new CommerceReleaseCampaignDO().setStatus(ReleaseStatusEnum.RUNNING.getStatus()),
                new LambdaUpdateWrapper<CommerceReleaseCampaignDO>()
                        .eq(CommerceReleaseCampaignDO::getStatus, ReleaseStatusEnum.SCHEDULED.getStatus())
                        .le(CommerceReleaseCampaignDO::getStartTime, now)
                        .gt(CommerceReleaseCampaignDO::getEndTime, now));
    }

    default int finishExpired(LocalDateTime now) {
        return update(new CommerceReleaseCampaignDO().setStatus(ReleaseStatusEnum.ENDED.getStatus()),
                new LambdaUpdateWrapper<CommerceReleaseCampaignDO>()
                        .in(CommerceReleaseCampaignDO::getStatus, ReleaseStatusEnum.SCHEDULED.getStatus(), ReleaseStatusEnum.RUNNING.getStatus())
                        .le(CommerceReleaseCampaignDO::getEndTime, now));
    }
}
