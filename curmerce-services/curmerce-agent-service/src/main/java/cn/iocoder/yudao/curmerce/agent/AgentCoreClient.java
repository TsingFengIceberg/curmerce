package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.curmerce.cloud.api.CoreOrderStatusRespDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Least-privilege Core contract used by Agent tools. */
@Component
public class AgentCoreClient {
    private final RestClient client;
    private final AgentServiceProperties properties;
    private final ObjectMapper objectMapper;

    public AgentCoreClient(RestClient.Builder builder, AgentServiceProperties properties, ObjectMapper objectMapper) {
        this.client = builder.baseUrl(properties.coreBaseUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Long authenticate(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isBlank()) throw new AgentAuthorizationException("缺少用户认证令牌");
        JsonNode body = post("/internal-api/curmerce/core/auth/check", Map.of("token", token));
        long userId = body.path("data").path("userId").asLong(0);
        if (userId <= 0) throw new AgentAuthorizationException("用户认证令牌无效");
        return userId;
    }

    public CoreOrderStatusRespDTO getOwnOrderStatus(String authorization, Long orderId) {
        Long userId = authenticate(authorization);
        JsonNode body = get("/internal-api/curmerce/core/order/" + userId + "/" + orderId + "/status");
        try {
            return objectMapper.treeToValue(body.path("data"), CoreOrderStatusRespDTO.class);
        } catch (Exception ex) {
            throw new AgentServiceException("Core 订单状态响应无效", ex);
        }
    }

    public JsonNode requestRefund(String authorization, Long orderId, String reason) {
        authenticate(authorization);
        if (orderId == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("退款订单和原因不能为空");
        }
        try {
            JsonNode body = client.post().uri("/commerce/refund/apply")
                    .header("Authorization", authorization)
                    .header("tenant-id", AgentRequestContext.tenantId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("orderId", orderId, "reason", reason.trim()))
                    .retrieve().body(JsonNode.class);
            check(body);
            return body.path("data");
        } catch (AgentServiceException | AgentAuthorizationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AgentServiceException("退款服务暂时不可用", ex);
        }
    }

    public JsonNode getOwnRefundStatus(String authorization, Long orderId) {
        Long userId = authenticate(authorization);
        JsonNode body = get("/internal-api/curmerce/core/refund/" + userId + "/" + orderId + "/status");
        return body.path("data");
    }

    private JsonNode post(String path, Object request) {
        requireInternalToken();
        try {
            JsonNode body = client.post().uri(path).header("X-Curmerce-Internal-Token", properties.internalToken())
                    .header("tenant-id", AgentRequestContext.tenantId())
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
            check(body);
            return body;
        } catch (AgentServiceException | AgentAuthorizationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AgentServiceException("Core 服务暂时不可用", ex);
        }
    }

    private JsonNode get(String path) {
        requireInternalToken();
        try {
            JsonNode body = client.get().uri(path).header("X-Curmerce-Internal-Token", properties.internalToken())
                    .header("tenant-id", AgentRequestContext.tenantId())
                    .retrieve().body(JsonNode.class);
            check(body);
            return body;
        } catch (AgentServiceException | AgentAuthorizationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AgentServiceException("Core 服务暂时不可用", ex);
        }
    }

    private void requireInternalToken() {
        if (properties.internalToken().isBlank()) throw new AgentServiceException("Agent 未配置 Core 内部令牌");
    }

    private static void check(JsonNode body) {
        if (body == null || body.path("code").asInt(-1) != 0) throw new AgentServiceException("Core 契约调用失败");
    }

    public static class AgentAuthorizationException extends RuntimeException {
        public AgentAuthorizationException(String message) { super(message); }
    }
    public static class AgentServiceException extends RuntimeException {
        public AgentServiceException(String message) { super(message); }
        public AgentServiceException(String message, Throwable cause) { super(message, cause); }
    }
}
