package cn.iocoder.yudao.module.member.controller.app.address;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.controller.app.address.vo.*;
import cn.iocoder.yudao.module.member.service.address.MemberAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 买家收货地址")
@RestController
@RequestMapping("/member/address")
@Validated
public class MemberAddressController {
    @Resource private MemberAddressService addressService;
    @PostMapping("/create") @Operation(summary = "创建收货地址")
    public CommonResult<Long> create(@Valid @RequestBody MemberAddressCreateReqVO reqVO) { return success(addressService.createAddress(getLoginUserId(), reqVO)); }
    @PutMapping("/update") @Operation(summary = "更新收货地址")
    public CommonResult<Boolean> update(@Valid @RequestBody MemberAddressUpdateReqVO reqVO) { addressService.updateAddress(getLoginUserId(), reqVO); return success(true); }
    @DeleteMapping("/delete") public CommonResult<Boolean> delete(@RequestParam Long id) { addressService.deleteAddress(getLoginUserId(), id); return success(true); }
    @GetMapping("/get") public CommonResult<MemberAddressRespVO> get(@RequestParam Long id) { return success(addressService.getAddress(getLoginUserId(), id)); }
    @GetMapping("/get-default") public CommonResult<MemberAddressRespVO> getDefault() { return success(addressService.getDefaultAddress(getLoginUserId())); }
    @GetMapping("/list") public CommonResult<List<MemberAddressRespVO>> list() { return success(addressService.getAddressList(getLoginUserId())); }
}
