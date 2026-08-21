package cn.iocoder.yudao.module.community.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityReportPageReqVO extends PageParam {
    private Integer status;
}
