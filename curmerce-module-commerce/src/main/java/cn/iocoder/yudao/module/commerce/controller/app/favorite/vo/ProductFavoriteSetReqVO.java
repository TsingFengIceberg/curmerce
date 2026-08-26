package cn.iocoder.yudao.module.commerce.controller.app.favorite.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductFavoriteSetReqVO {

    @NotNull(message = "商品编号不能为空")
    private Long productId;

    @NotNull(message = "收藏状态不能为空")
    private Boolean favorite;
}
