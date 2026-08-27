package cn.iocoder.yudao.module.infra.controller.app.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "媒体预签名直传票据 Response VO")
@Data
@AllArgsConstructor
public class MediaUploadTicketRespVO {
    private String ticketKey;
    private String uploadUrl;
    private Map<String, String> requiredHeaders;
    private String assetUrl;
    private LocalDateTime expiresAt;
}
