package cn.iocoder.yudao.module.commerce.controller.admin.store;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.commerce.controller.admin.store.vo.StoreRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.store.vo.StoreUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.service.store.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 店铺")
@RestController
@RequestMapping("/commerce/store")
@Validated
public class StoreController {
    @Resource private StoreService storeService;

    @GetMapping("/get-own")
    @Operation(summary = "查询自己的店铺")
    @PreAuthorize("@ss.hasPermission('commerce:store:self-query')")
    public CommonResult<StoreRespVO> getOwn() {
        return success(BeanUtils.toBean(storeService.getOwnStore(), StoreRespVO.class));
    }

    @PutMapping("/update-own")
    @Operation(summary = "修改自己的店铺")
    @PreAuthorize("@ss.hasPermission('commerce:store:self-update')")
    public CommonResult<Boolean> updateOwn(@Valid @RequestBody StoreUpdateOwnReqVO reqVO) {
        storeService.updateOwnStore(reqVO);
        return success(true);
    }
}
