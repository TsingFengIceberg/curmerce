package cn.iocoder.yudao.module.commerce.controller.app.cart.vo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class CartAddReqVO {
    @NotNull private Long skuId;
    @NotNull @Min(1) @Max(99) private Integer quantity;
}
