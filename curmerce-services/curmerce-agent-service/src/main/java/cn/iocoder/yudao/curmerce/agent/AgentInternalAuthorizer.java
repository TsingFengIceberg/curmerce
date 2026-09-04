package cn.iocoder.yudao.curmerce.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Centralizes constant-time checks for Agent's internal endpoints. */
final class AgentInternalAuthorizer {
    private AgentInternalAuthorizer() { }

    static boolean matches(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
