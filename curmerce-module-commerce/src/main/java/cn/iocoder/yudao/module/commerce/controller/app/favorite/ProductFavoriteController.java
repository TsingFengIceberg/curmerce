package cn.iocoder.yudao.module.commerce.controller.app.favorite;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.favorite.vo.ProductFavoriteRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.favorite.vo.ProductFavoriteSetReqVO;
import cn.iocoder.yudao.module.commerce.service.favorite.ProductFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 商品收藏")
@RestController
@RequestMapping("/commerce/product-favorite")
@Validated
public class ProductFavoriteController {

    @Resource
    private ProductFavoriteService favoriteService;

    @PutMapping("/set")
    @Operation(summary = "设置商品收藏状态")
    public CommonResult<Boolean> setFavorite(@Valid @RequestBody ProductFavoriteSetReqVO reqVO) {
        favoriteService.setFavorite(getLoginUserId(), reqVO.getProductId(), reqVO.getFavorite());
        return success(true);
    }

    @GetMapping("/status")
    @Operation(summary = "查询商品收藏状态")
    public CommonResult<Boolean> getFavoriteStatus(@RequestParam Long productId) {
        return success(favoriteService.isFavorite(getLoginUserId(), productId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询我的商品收藏")
    public CommonResult<PageResult<ProductFavoriteRespVO>> getFavoritePage(@Valid PageParam pageParam) {
        return success(favoriteService.getFavoritePage(getLoginUserId(), pageParam));
    }
}
