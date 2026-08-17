package cn.iocoder.yudao.module.commerce.dal.dataobject.order;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@TableName(value = "commerce_order_item", autoResultMap = true)
@KeySequence("commerce_order_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceOrderItemDO extends BaseDO {
    @TableId private Long id;
    private Long orderId;
    private Long productId;
    private Long skuId;
    private String productName;
    private String productImageUrl;
    private String skuCode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ProductSkuDO.SpecificationValue> specificationValues;
    private String skuImageUrl;
    private Long price;
    private Integer quantity;
    private Long totalAmount;
}
