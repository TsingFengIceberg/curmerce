package cn.iocoder.yudao.module.commerce.controller.app.cart.vo;
import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.PublicProductSummaryRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.PublicProductSkuRespVO;
import lombok.Data;
@Data public class CartItemRespVO {
    private Long id; private Integer quantity; private Boolean selected;
    private PublicProductSummaryRespVO product; private PublicProductSkuRespVO sku; private String invalidReason;
}
