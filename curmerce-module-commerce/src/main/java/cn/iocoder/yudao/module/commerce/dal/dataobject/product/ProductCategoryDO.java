package cn.iocoder.yudao.module.commerce.dal.dataobject.product;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Platform-owned product category.
 */
@TableName("commerce_product_category")
@KeySequence("commerce_product_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductCategoryDO extends BaseDO {

    @TableId
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private String imageUrl;
    private Integer sort;
    /**
     * {@link CommonStatusEnum}
     */
    private Integer status;
}
