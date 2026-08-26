package cn.iocoder.yudao.module.member.controller.admin.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserRespVO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 买家")
@RestController
@RequestMapping("/member/user")
@Validated
public class MemberUserController {

    @Resource
    private MemberUserService userService;

    @GetMapping("/page")
    @Operation(summary = "查询买家分页")
    @PreAuthorize("@ss.hasPermission('commerce:order:query')")
    public CommonResult<PageResult<MemberUserRespVO>> page(@Valid MemberUserPageReqVO reqVO) {
        return success(BeanUtils.toBean(userService.getUserPage(reqVO), MemberUserRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "查询买家")
    @PreAuthorize("@ss.hasPermission('commerce:order:query')")
    public CommonResult<MemberUserRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(userService.getUser(id), MemberUserRespVO.class));
    }
}
