package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
@Slf4j
public class HttpMediaContentModerator implements MediaContentModerator {

    @Resource
    private CurmerceMediaProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public MediaModerationDecision moderate(byte[] content, String mimeType, String sha256) {
        CurmerceMediaProperties.Moderation config = properties.getModeration();
        if (!config.isEnabled()) return MediaModerationDecision.safe();
        if (StrUtil.isBlank(config.getEndpoint())) {
            return MediaModerationDecision.error("Moderation endpoint is not configured");
        }
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(config.getConnectTimeout()).build()) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                    .timeout(config.getRequestTimeout())
                    .header("Content-Type", mimeType)
                    .header("X-Content-SHA256", sha256)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(content));
            if (StrUtil.isNotBlank(config.getBearerToken())) {
                request.header("Authorization", "Bearer " + config.getBearerToken());
            }
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return MediaModerationDecision.error("Moderation endpoint returned HTTP " + response.statusCode());
            }
            JsonNode payload = objectMapper.readTree(response.body());
            String decision = payload.path("decision").asText("").trim().toUpperCase();
            String reason = payload.path("reason").asText(null);
            return switch (decision) {
                case "SAFE" -> MediaModerationDecision.safe();
                case "REVIEW" -> new MediaModerationDecision(MediaModerationDecision.Status.REVIEW, reason);
                case "REJECT" -> new MediaModerationDecision(MediaModerationDecision.Status.REJECT, reason);
                default -> MediaModerationDecision.error("Unknown moderation decision: " + decision);
            };
        } catch (Exception ex) {
            log.warn("[moderate][content moderation request failed]", ex);
            return MediaModerationDecision.error(ex.getClass().getSimpleName() + ": " + safeMessage(ex));
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null) return "no detail";
        return message.substring(0, Math.min(message.length(), 400));
    }
}
