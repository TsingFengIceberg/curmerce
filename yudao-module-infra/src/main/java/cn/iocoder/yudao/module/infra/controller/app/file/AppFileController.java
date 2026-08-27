package cn.iocoder.yudao.module.infra.controller.app.file;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.controller.app.file.vo.AppFileUploadReqVO;
import cn.iocoder.yudao.module.infra.controller.app.file.vo.MediaUploadCapabilitiesRespVO;
import cn.iocoder.yudao.module.infra.controller.app.file.vo.MediaUploadTicketReqVO;
import cn.iocoder.yudao.module.infra.controller.app.file.vo.MediaUploadTicketRespVO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.infra.service.file.MediaAssetContent;
import cn.iocoder.yudao.module.infra.service.file.MediaUploadService;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.UserRateLimiterKeyResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Tag(name = "用户 App - 文件存储")
@RestController
@RequestMapping("/infra/file")
@Validated
@Slf4j
public class AppFileController {

    @Resource
    private FileService fileService;

    @Resource
    private MediaUploadService mediaUploadService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    @Parameter(name = "file", description = "文件附件", required = true,
            schema = @Schema(type = "string", format = "binary"))
    @RateLimiter(count = 20, time = 1, timeUnit = TimeUnit.MINUTES,
            keyResolver = UserRateLimiterKeyResolver.class, message = "图片上传过于频繁，请稍后再试")
    public CommonResult<String> uploadFile(@Valid AppFileUploadReqVO uploadReqVO) throws Exception {
        MultipartFile file = uploadReqVO.getFile();
        byte[] content = IoUtil.readBytes(file.getInputStream());
        return success(fileService.createImage(content, file.getOriginalFilename(), uploadReqVO.getDirectory()));
    }

    @GetMapping("/upload-capabilities")
    @Operation(summary = "获取媒体上传能力")
    public CommonResult<MediaUploadCapabilitiesRespVO> getUploadCapabilities() {
        return success(mediaUploadService.getCapabilities());
    }

    @PostMapping("/upload-ticket")
    @Operation(summary = "申请媒体预签名直传票据")
    @RateLimiter(count = 20, time = 1, timeUnit = TimeUnit.MINUTES,
            keyResolver = UserRateLimiterKeyResolver.class, message = "图片上传过于频繁，请稍后再试")
    public CommonResult<MediaUploadTicketRespVO> issueUploadTicket(
            @Valid @RequestBody MediaUploadTicketReqVO request) {
        return success(mediaUploadService.issueTicket(request));
    }

    @PostMapping("/upload-ticket/{ticketKey}/finalize")
    @Operation(summary = "确认媒体预签名直传")
    public CommonResult<String> finalizeUploadTicket(@PathVariable("ticketKey") String ticketKey) {
        return success(mediaUploadService.finalizeTicket(ticketKey));
    }

    @GetMapping("/assets/{assetKey}")
    @PermitAll
    @Operation(summary = "读取稳定媒体资产")
    public void getMediaAsset(HttpServletRequest request, HttpServletResponse response,
                              @PathVariable("assetKey") String assetKey,
                              @RequestParam(value = "variant", required = false) String variant) throws Exception {
        MediaAssetContent asset = fileService.getMediaAsset(assetKey, variant);
        String sha256 = StrUtil.blankToDefault(asset.file().getSha256(), DigestUtil.sha256Hex(asset.content()));
        String etag = '"' + sha256 + '"';
        if (etag.equals(request.getHeader("If-None-Match"))) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        response.setContentType(asset.file().getType());
        response.setContentLengthLong(asset.content().length);
        response.setHeader("ETag", etag);
        if (Integer.valueOf(10).equals(asset.file().getVisibility())) {
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Vary", "Authorization");
        } else {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        String filename = StrUtil.blankToDefault(asset.file().getName(), asset.file().getAssetKey());
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + encodedName);
        response.getOutputStream().write(asset.content());
    }

}
