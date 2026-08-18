package cn.iocoder.yudao.module.commerce.controller.admin.refund.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.commerce.enums.refund.RefundStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RefundPageReqVO extends PageParam {
    @InEnum(RefundStatusEnum.class)
    private Integer status;
    private String orderNo;
    private Long memberUserId;
}
