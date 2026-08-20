package cn.iocoder.yudao.module.commerce.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceOrderPageReqVO extends PageParam {
    @InEnum(OrderStatusEnum.class)
    private Integer status;
    @Size(max = 40)
    private String orderNo;
    private Long merchantId;
    private Long memberUserId;
}
