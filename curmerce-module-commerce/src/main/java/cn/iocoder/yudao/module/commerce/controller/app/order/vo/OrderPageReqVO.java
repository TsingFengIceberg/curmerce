package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.commerce.enums.order.OrderStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPageReqVO extends PageParam {
    @InEnum(OrderStatusEnum.class)
    private Integer status;
}
