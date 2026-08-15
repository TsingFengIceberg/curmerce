package cn.iocoder.yudao.module.commerce.controller.app.cart;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.commerce.controller.app.cart.vo.*;
import cn.iocoder.yudao.module.commerce.service.cart.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 买家购物车")
@RestController
@RequestMapping("/commerce/cart")
@Validated
public class CartController {
    @Resource private CartService cartService;
    @PostMapping("/add") @Operation(summary = "加入购物车") public CommonResult<Long> add(@Valid @RequestBody CartAddReqVO req) { return success(cartService.add(getLoginUserId(), req)); }
    @PutMapping("/update-quantity") public CommonResult<Boolean> updateQuantity(@Valid @RequestBody CartQuantityUpdateReqVO req) { cartService.updateQuantity(getLoginUserId(), req); return success(true); }
    @PutMapping("/update-selected") public CommonResult<Boolean> updateSelected(@Valid @RequestBody CartSelectionUpdateReqVO req) { cartService.updateSelected(getLoginUserId(), req); return success(true); }
    @DeleteMapping("/delete") public CommonResult<Boolean> delete(@RequestParam @NotEmpty @Size(max = 100) List<Long> ids) {
        CartBatchReqVO req = new CartBatchReqVO();
        req.setIds(ids);
        cartService.delete(getLoginUserId(), req);
        return success(true);
    }
    @GetMapping("/list") public CommonResult<CartListRespVO> list() { return success(cartService.list(getLoginUserId())); }
    @GetMapping("/count") public CommonResult<Long> count() { return success(cartService.count(getLoginUserId())); }
}
