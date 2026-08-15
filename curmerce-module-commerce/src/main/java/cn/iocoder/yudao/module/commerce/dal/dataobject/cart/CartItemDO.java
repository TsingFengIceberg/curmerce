package cn.iocoder.yudao.module.commerce.dal.dataobject.cart;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@TableName("commerce_cart_item")
@Data
public class CartItemDO {
    @TableId private Long id;
    private Long memberUserId;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private Boolean selected;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
