package cn.iocoder.yudao.module.commerce.dal.dataobject.favorite;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("commerce_product_favorite")
@Data
public class ProductFavoriteDO {

    @TableId
    private Long id;
    private Long memberUserId;
    private Long productId;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
