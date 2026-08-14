package cn.iocoder.yudao.module.commerce.dal.dataobject.product;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.commerce.enums.product.ProductAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ordinary merchant product (SPU-like descriptive aggregate).
 */
@TableName(value = "commerce_product", autoResultMap = true)
@KeySequence("commerce_product_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductDO extends BaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    private Long storeId;
    private Long categoryId;
    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String subtitle;
    private String mainImageUrl;
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private List<String> imageUrls;
    private String description;
    /**
     * {@link ProductAuditStatusEnum}
     */
    private Integer auditStatus;
    /**
     * {@link ProductSaleStatusEnum}
     */
    private Integer saleStatus;
    private Long reviewerUserId;
    private LocalDateTime reviewTime;
    private String rejectReason;
    private Integer sort;
}
