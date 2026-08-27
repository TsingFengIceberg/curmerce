package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class ClamAvMediaContentScanner implements MediaContentScanner {

    private static final byte[] EICAR_MARKER = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE"
            .getBytes(StandardCharsets.US_ASCII);

    @Resource
    private CurmerceMediaProperties properties;

    @Override
    public MediaScanResult scan(byte[] content) {
        if (contains(content, EICAR_MARKER)) {
            return MediaScanResult.rejected("EICAR test signature");
        }
        CurmerceMediaProperties.ClamAv config = properties.getClamAv();
        if (!config.isEnabled()) {
            return MediaScanResult.skipped("ClamAV disabled");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.getHost(), config.getPort()),
                    Math.toIntExact(config.getConnectTimeout().toMillis()));
            socket.setSoTimeout(Math.toIntExact(config.getReadTimeout().toMillis()));
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            int offset = 0;
            while (offset < content.length) {
                int length = Math.min(8192, content.length - offset);
                output.writeInt(length);
                output.write(content, offset, length);
                offset += length;
            }
            output.writeInt(0);
            output.flush();
            String response = readResponse(socket.getInputStream());
            if (response.endsWith("OK")) {
                return MediaScanResult.clean();
            }
            if (response.contains("FOUND")) {
                return MediaScanResult.rejected(response);
            }
            log.warn("[scan][ClamAV returned an unexpected response: {}]", response);
            return config.isFailClosed()
                    ? MediaScanResult.error("Unexpected ClamAV response")
                    : MediaScanResult.skipped("ClamAV response: " + response);
        } catch (Exception ex) {
            log.warn("[scan][ClamAV unavailable, upload remains auditable but is not marked clean]", ex);
            return config.isFailClosed()
                    ? MediaScanResult.error("ClamAV unavailable")
                    : MediaScanResult.skipped("ClamAV unavailable");
        }
    }

    private static String readResponse(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) >= 0 && value != 0 && buffer.size() < 4096) {
            buffer.write(value);
        }
        return buffer.toString(StandardCharsets.UTF_8).trim();
    }

    private static boolean contains(byte[] source, byte[] marker) {
        outer:
        for (int i = 0; i <= source.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (source[i + j] != marker[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
