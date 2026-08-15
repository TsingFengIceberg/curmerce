package cn.iocoder.yudao.module.commerce.controller.app.cart.vo;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
@Data public class CartBatchReqVO { @NotEmpty @Size(max = 100) private List<Long> ids; }
