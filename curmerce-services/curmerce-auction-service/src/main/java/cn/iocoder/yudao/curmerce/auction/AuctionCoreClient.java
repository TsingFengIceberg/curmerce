package cn.iocoder.yudao.curmerce.auction;

import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionItemCheckReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionItemCheckRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionOrderReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionOrderRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CorePermissionCheckReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreTokenCheckReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreMerchantOwnerRespDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Typed Core contract used by Auction; it never reads Core tables directly. */
@Component
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
public class AuctionCoreClient {
    private final RestClient client;
    private final AuctionServiceProperties properties;
    private final ObjectMapper objectMapper;

    public AuctionCoreClient(RestClient.Builder builder, AuctionServiceProperties properties, ObjectMapper objectMapper) {
        this.client = builder.baseUrl(properties.coreBaseUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Long authenticate(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isBlank()) throw unavailable("拍卖请求缺少认证令牌");
        JsonNode body = post("/internal-api/curmerce/core/auth/check", new CoreTokenCheckReqDTO().setToken(token));
        JsonNode data = body.get("data");
        JsonNode userIdNode = data == null ? null : data.get("userId");
        Long userId = userIdNode == null || userIdNode.isNull() ? null : userIdNode.asLong();
        if (userId == null || userId <= 0) throw unavailable("拍卖用户身份无效");
        return userId;
    }

    public boolean hasPermission(Long userId, String permission) {
        CorePermissionCheckReqDTO request = new CorePermissionCheckReqDTO().setUserId(userId)
                .setValues(List.of(permission)).setType(CorePermissionCheckReqDTO.TYPE_PERMISSION);
        JsonNode body = post("/internal-api/curmerce/core/permission/check", request);
        return body.path("data").asBoolean(false);
    }

    public CoreAuctionItemCheckRespDTO checkItem(Long userId, Long productId, Long skuId) {
        CoreAuctionItemCheckReqDTO request = new CoreAuctionItemCheckReqDTO().setUserId(userId)
                .setProductId(productId).setSkuId(skuId);
        JsonNode body = post("/internal-api/curmerce/core/auction/item/check", request);
        if (body.get("data") == null || body.get("data").isNull()) return null;
        try {
            return objectMapper.treeToValue(body.get("data"), CoreAuctionItemCheckRespDTO.class);
        } catch (Exception ex) {
            throw unavailable("Core 商品校验响应无效");
        }
    }

    public CoreAuctionOrderRespDTO createSettlementOrder(CoreAuctionOrderReqDTO request) {
        JsonNode body = post("/internal-api/curmerce/core/auction/settlement-order", request);
        try {
            return objectMapper.treeToValue(body.get("data"), CoreAuctionOrderRespDTO.class);
        } catch (Exception ex) {
            throw unavailable("Core 结算订单响应无效");
        }
    }

    public CoreMerchantOwnerRespDTO merchantOwner(Long userId) {
        JsonNode body = get("/internal-api/curmerce/core/auction/owner/" + userId);
        if (body.get("data") == null || body.get("data").isNull()) return null;
        try {
            return objectMapper.treeToValue(body.get("data"), CoreMerchantOwnerRespDTO.class);
        } catch (Exception ex) {
            throw unavailable("Core 商家归属响应无效");
        }
    }

    private JsonNode post(String path, Object request) {
        if (properties.coreInternalToken().isBlank()) throw unavailable("拍卖服务未配置 Core 内部令牌");
        try {
            JsonNode body = client.post().uri(path)
                    .header("X-Curmerce-Internal-Token", properties.coreInternalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(JsonNode.class);
            if (body == null || body.path("code").asInt(-1) != 0) throw unavailable("Core 拍卖契约调用失败");
            return body;
        } catch (RuntimeException ex) {
            if (ex instanceof AuctionCoreUnavailableException) throw ex;
            throw unavailable("Core 拍卖契约暂时不可用");
        }
    }

    private JsonNode get(String path) {
        if (properties.coreInternalToken().isBlank()) throw unavailable("拍卖服务未配置 Core 内部令牌");
        try {
            JsonNode body = client.get().uri(path)
                    .header("X-Curmerce-Internal-Token", properties.coreInternalToken())
                    .retrieve().body(JsonNode.class);
            if (body == null || body.path("code").asInt(-1) != 0) throw unavailable("Core 拍卖契约调用失败");
            return body;
        } catch (RuntimeException ex) {
            if (ex instanceof AuctionCoreUnavailableException) throw ex;
            throw unavailable("Core 拍卖契约暂时不可用");
        }
    }

    private AuctionCoreUnavailableException unavailable(String message) {
        return new AuctionCoreUnavailableException(message);
    }

    public static class AuctionCoreUnavailableException extends RuntimeException {
        public AuctionCoreUnavailableException(String message) { super(message); }
    }
}
