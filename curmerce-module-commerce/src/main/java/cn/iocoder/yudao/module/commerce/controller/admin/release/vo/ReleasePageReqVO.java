package cn.iocoder.yudao.module.commerce.controller.admin.release.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class ReleasePageReqVO extends PageParam {
    private Integer status;
    private String name;
}
