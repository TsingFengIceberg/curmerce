package cn.iocoder.yudao.module.member.controller.admin.user.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MemberUserPageReqVO extends PageParam {
    private String keyword;
    private Integer status;
}
