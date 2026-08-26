package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductOperationLogRespVO {

    private Long id;
    private Long productId;
    private Long operatorUserId;
    private Integer operatorType;
    private String action;
    private Integer fromAuditStatus;
    private Integer toAuditStatus;
    private Integer fromSaleStatus;
    private Integer toSaleStatus;
    private String remark;
    private LocalDateTime createTime;
}
