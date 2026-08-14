package cn.iocoder.yudao.module.commerce.dal.dataobject.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sellable product SKU. Price and stock are authoritative here.
 */
@TableName(value = "commerce_product_sku", autoResultMap = true)
@KeySequence("commerce_product_sku_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductSkuDO extends BaseDO {

    @TableId
    private Long id;
    private Long productId;
    private Long merchantId;
    private String code;
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private List<SpecificationValue> specificationValues;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String imageUrl;
    /** Unit: fen (one hundredth of the currency unit). */
    private Long price;
    /** Unit: fen; nullable comparison price. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long marketPrice;
    private Integer stock;
    /**
     * {@link CommonStatusEnum}
     */
    private Integer status;
    private Integer sort;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecificationValue {
        private String name;
        private String value;
    }
}
