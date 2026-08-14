package cn.iocoder.yudao.framework.apilog.core.util;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Removes sensitive fields before request or response data is persisted.
 * Malformed JSON is omitted rather than returned to callers.
 */
public final class ApiLogSanitizeUtils {

    private static final Set<String> DEFAULT_KEYS = Set.of("password", "token", "accessToken", "refreshToken");

    private ApiLogSanitizeUtils() {
    }

    public static String sanitizeMap(Map<String, ?> source, String[] additionalKeys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        try {
            ObjectNode node = JsonUtils.getObjectMapper().createObjectNode();
            source.forEach((key, value) -> node.set(key, JsonUtils.getObjectMapper().valueToTree(value)));
            sanitize(node, keys(additionalKeys));
            return JsonUtils.toJsonString(node);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String sanitizeJson(String json, String[] additionalKeys) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            // Do not use JsonUtils.parseTree here: its failure path logs the original body.
            JsonNode node = JsonUtils.getObjectMapper().readTree(json);
            if (node == null) {
                return null;
            }
            sanitize(node, keys(additionalKeys));
            return JsonUtils.toJsonString(node);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String sanitizeResult(CommonResult<?> result, String[] additionalKeys) {
        if (result == null) {
            return null;
        }
        try {
            JsonNode root = JsonUtils.getObjectMapper().readTree(JsonUtils.toJsonString(result));
            if (root != null && root.has("data")) {
                sanitize(root.get("data"), keys(additionalKeys));
            }
            return root == null ? null : JsonUtils.toJsonString(root);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Set<String> keys(String[] additionalKeys) {
        Set<String> keys = new HashSet<>(DEFAULT_KEYS);
        if (additionalKeys != null) {
            for (String key : additionalKeys) {
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    private static void sanitize(JsonNode node, Set<String> keys) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> sanitize(child, keys));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if (keys.contains(entry.getKey())) {
                iterator.remove();
            } else {
                sanitize(entry.getValue(), keys);
            }
        }
    }
}
