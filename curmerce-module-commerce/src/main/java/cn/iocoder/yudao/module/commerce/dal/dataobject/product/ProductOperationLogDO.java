package cn.iocoder.yudao.module.commerce.dal.dataobject.product;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("commerce_product_operation_log")
@Data
public class ProductOperationLogDO {

    @TableId
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
