package cn.iocoder.yudao.curmerce.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable non-reversible principal fingerprint used for Redis keys and audit records. */
final class AgentPrincipalHasher {
    private AgentPrincipalHasher() { }

    static String hash(String principal) {
        String value = principal == null || principal.isBlank() ? "anonymous" : principal;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int i = 0; i < 8; i++) result.append(String.format("%02x", digest[i]));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
