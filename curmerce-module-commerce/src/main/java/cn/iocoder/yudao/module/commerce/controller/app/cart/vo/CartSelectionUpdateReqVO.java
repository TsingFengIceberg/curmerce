package cn.iocoder.yudao.module.commerce.controller.app.cart.vo;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CartSelectionUpdateReqVO {
    @NotEmpty @Size(max = 100)
    private List<Long> ids;
    @NotNull
    private Boolean selected;
}
