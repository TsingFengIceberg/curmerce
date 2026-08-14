package cn.iocoder.yudao.framework.apilog.core.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiLogSanitizeUtilsTest {

    @Test
    void sanitizeJson_removesNestedSensitiveKeys() {
        String result = ApiLogSanitizeUtils.sanitizeJson(
                "{\"password\":\"secret-value\",\"items\":[{\"token\":\"token-value\",\"name\":\"ok\"}]}", null);
        assertNotNull(result);
        assertFalse(result.contains("secret-value"));
        assertFalse(result.contains("token-value"));
        assertTrue(result.contains("ok"));
    }

    @Test
    void sanitizeJson_malformedInputFailsClosed() {
        assertNull(ApiLogSanitizeUtils.sanitizeJson("not-json secret-value", null));
    }

    @Test
    void sanitizeMap_doesNotMutateSourceAndSupportsExtraKeys() {
        Map<String, Object> source = Map.of("password", "secret-value", "privateKey", "private-value", "name", "ok");
        String result = ApiLogSanitizeUtils.sanitizeMap(source, new String[]{"privateKey"});
        assertFalse(result.contains("secret-value"));
        assertFalse(result.contains("private-value"));
        assertTrue(result.contains("ok"));
        assertEquals("secret-value", source.get("password"));
    }
}
