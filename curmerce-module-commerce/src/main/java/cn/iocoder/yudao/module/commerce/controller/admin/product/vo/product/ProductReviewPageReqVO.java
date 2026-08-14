package cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductReviewPageReqVO extends PageParam {
    private Long merchantId;
    private Long storeId;
    private Long categoryId;
    private String code;
    private String name;
    private Integer auditStatus;
    private Integer saleStatus;
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
