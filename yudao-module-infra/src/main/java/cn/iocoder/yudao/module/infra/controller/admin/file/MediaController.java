package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.media.MediaModerationReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.media.MediaMigrationReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.media.MediaMigrationRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.infra.service.file.MediaManagementService;
import cn.iocoder.yudao.module.infra.service.file.MediaMigrationService;
import cn.iocoder.yudao.module.infra.service.file.MediaAssetContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 媒体资产治理")
@RestController
@RequestMapping("/infra/media")
@Validated
@PreAuthorize("@ss.hasPermission('infra:file:query')")
public class MediaController {

    @Resource private FileService fileService;
    @Resource private MediaManagementService managementService;
    @Resource private MediaMigrationService migrationService;

    @GetMapping("/page")
    @Operation(summary = "分页查询媒体资产")
    public CommonResult<PageResult<FileRespVO>> page(@Valid FilePageReqVO request) {
        if (request.getOriginalOnly() == null) request.setOriginalOnly(true);
        PageResult<FileDO> page = fileService.getFilePage(request);
        return success(BeanUtils.toBean(page, FileRespVO.class));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "认证预览媒体资产")
    public void content(@PathVariable("id") Long id,
                        @RequestParam(value = "variant", required = false) String variant,
                        HttpServletResponse response) throws Exception {
        MediaAssetContent asset = fileService.getManagedMediaAsset(id, variant);
        response.setContentType(asset.file().getType());
        response.setContentLength(asset.content().length);
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(asset.content());
    }

    @PostMapping("/{id}/quarantine")
    @Operation(summary = "隔离媒体资产")
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Boolean> quarantine(@PathVariable("id") Long id,
                                             @Valid @RequestBody MediaModerationReqVO request) {
        managementService.quarantine(id, request.getReason());
        return success(true);
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "解除媒体隔离")
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Boolean> release(@PathVariable("id") Long id,
                                          @Valid @RequestBody MediaModerationReqVO request) {
        managementService.release(id, request.getReason());
        return success(true);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "拒绝媒体资产")
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Boolean> reject(@PathVariable("id") Long id,
                                         @Valid @RequestBody MediaModerationReqVO request) {
        managementService.reject(id, request.getReason());
        return success(true);
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "重新执行内容审核与衍生处理")
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Boolean> retry(@PathVariable("id") Long id) {
        managementService.retry(id);
        return success(true);
    }

    @PostMapping("/migration")
    @Operation(summary = "批量迁移媒体资产到目标文件配置")
    @PreAuthorize("@ss.hasPermission('infra:file-config:update')")
    public CommonResult<MediaMigrationRespVO> migrate(@Valid @RequestBody MediaMigrationReqVO request) {
        return success(migrationService.migrate(request));
    }
}
