package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClamAvMediaContentScannerTest {

    private final CurmerceMediaProperties properties = new CurmerceMediaProperties();
    private final ClamAvMediaContentScanner scanner = new ClamAvMediaContentScanner();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scanner, "properties", properties);
    }

    @Test
    void alwaysRejectsEicarMarker() {
        MediaScanResult result = scanner.scan(
                "prefix EICAR-STANDARD-ANTIVIRUS-TEST-FILE suffix".getBytes(StandardCharsets.US_ASCII));
        assertEquals(MediaScanResult.Status.REJECTED, result.status());
    }

    @Test
    void failsClosedWhenEnabledScannerIsUnavailable() {
        CurmerceMediaProperties.ClamAv config = properties.getClamAv();
        config.setEnabled(true);
        config.setFailClosed(true);
        config.setHost("127.0.0.1");
        config.setPort(1);
        config.setConnectTimeout(Duration.ofMillis(50));
        config.setReadTimeout(Duration.ofMillis(50));
        assertEquals(MediaScanResult.Status.ERROR, scanner.scan(new byte[]{1, 2, 3}).status());
    }

    @Test
    void failsClosedOnUnexpectedScannerResponse() throws Exception {
        CurmerceMediaProperties.ClamAv config = properties.getClamAv();
        config.setEnabled(true);
        config.setFailClosed(true);
        config.setHost("127.0.0.1");
        config.setConnectTimeout(Duration.ofSeconds(1));
        config.setReadTimeout(Duration.ofSeconds(1));
        try (ServerSocket server = new ServerSocket(0)) {
            config.setPort(server.getLocalPort());
            CompletableFuture<Void> responder = CompletableFuture.runAsync(() -> {
                try (Socket socket = server.accept()) {
                    socket.getOutputStream().write("stream: UNKNOWN\0".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            assertEquals(MediaScanResult.Status.ERROR, scanner.scan(new byte[]{1, 2, 3}).status());
            responder.join();
        }
    }
}
