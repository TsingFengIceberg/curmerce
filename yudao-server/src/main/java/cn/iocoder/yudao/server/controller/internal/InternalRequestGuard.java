package cn.iocoder.yudao.server.controller.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalRequestGuard {

    private final byte[] expectedToken;

    public InternalRequestGuard(@Value("${curmerce.cloud.internal-token:}") String expectedToken) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    public void check(String actualToken) {
        byte[] actual = actualToken == null ? new byte[0] : actualToken.getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length < 32 || !MessageDigest.isEqual(expectedToken, actual)) {
            throw new AccessDeniedException("invalid internal service credential");
        }
    }
}
