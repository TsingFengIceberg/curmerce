package cn.iocoder.yudao.module.community.dal.mysql.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.community.controller.admin.vo.CommunityReportPageReqVO;
import cn.iocoder.yudao.module.community.dal.dataobject.report.CommunityReportDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommunityReportMapper extends BaseMapperX<CommunityReportDO> {
    default PageResult<CommunityReportDO> selectAdminPage(CommunityReportPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<CommunityReportDO>()
                .eqIfPresent(CommunityReportDO::getStatus, req.getStatus())
                .orderByDesc(CommunityReportDO::getId));
    }
}
