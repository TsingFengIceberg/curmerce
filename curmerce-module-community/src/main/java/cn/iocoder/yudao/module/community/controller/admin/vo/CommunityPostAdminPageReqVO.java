package cn.iocoder.yudao.module.community.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityPostAdminPageReqVO extends PageParam {
    private Integer status;
    private String keyword;
}
