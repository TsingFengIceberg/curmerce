package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderDetailRespVO extends OrderSummaryRespVO {
    private String receiverName;
    private String receiverMobile;
    private Integer receiverAreaId;
    private String receiverAreaName;
    private String receiverDetailAddress;
    private List<OrderItemRespVO> items;
}
