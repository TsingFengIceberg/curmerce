package cn.iocoder.yudao.module.commerce.controller.app.favorite.vo;

import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.PublicProductSummaryRespVO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductFavoriteRespVO {

    private Long id;
    private Long productId;
    private LocalDateTime favoriteTime;
    private PublicProductSummaryRespVO product;
}
