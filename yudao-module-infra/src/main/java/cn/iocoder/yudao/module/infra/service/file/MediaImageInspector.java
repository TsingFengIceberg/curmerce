package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_IMAGE_DIMENSIONS_INVALID;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TOO_LARGE;

@Component
public class MediaImageInspector {

    @Resource
    private CurmerceMediaProperties properties;

    public MediaImageMetadata inspect(byte[] content, String name) {
        if (content.length > properties.getMaxUploadSize().toBytes()) {
            throw exception(FILE_UPLOAD_TOO_LARGE, properties.getMaxUploadSize().toMegabytes());
        }
        String mimeType = FileTypeUtils.getMineType(content, name).toLowerCase(Locale.ROOT);
        if (!properties.getAllowedMimeTypes().contains(mimeType)) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }
        int[] dimensions = "image/webp".equals(mimeType) ? readWebpDimensions(content) : readImageIoDimensions(content);
        long pixels = (long) dimensions[0] * dimensions[1];
        if (dimensions[0] <= 0 || dimensions[1] <= 0
                || dimensions[0] > properties.getMaxWidth()
                || dimensions[1] > properties.getMaxHeight()
                || pixels > properties.getMaxPixels()) {
            throw exception(FILE_IMAGE_DIMENSIONS_INVALID, properties.getMaxWidth(), properties.getMaxHeight(),
                    properties.getMaxPixels());
        }
        return new MediaImageMetadata(mimeType, extension(mimeType), dimensions[0], dimensions[1]);
    }

    private int[] readImageIoDimensions(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw exception(FILE_TYPE_NOT_ALLOWED);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }
    }

    private int[] readWebpDimensions(byte[] data) {
        if (data.length < 30 || !asciiEquals(data, 0, "RIFF") || !asciiEquals(data, 8, "WEBP")) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }
        if (asciiEquals(data, 12, "VP8X")) {
            return new int[]{1 + littleEndian24(data, 24), 1 + littleEndian24(data, 27)};
        }
        if (asciiEquals(data, 12, "VP8L") && (data[20] & 0xff) == 0x2f) {
            int b0 = data[21] & 0xff;
            int b1 = data[22] & 0xff;
            int b2 = data[23] & 0xff;
            int b3 = data[24] & 0xff;
            return new int[]{1 + b0 + ((b1 & 0x3f) << 8),
                    1 + ((b1 & 0xc0) >> 6) + (b2 << 2) + ((b3 & 0x0f) << 10)};
        }
        if (asciiEquals(data, 12, "VP8 ") && data.length >= 30
                && (data[23] & 0xff) == 0x9d && (data[24] & 0xff) == 0x01 && (data[25] & 0xff) == 0x2a) {
            return new int[]{littleEndian16(data, 26) & 0x3fff, littleEndian16(data, 28) & 0x3fff};
        }
        throw exception(FILE_TYPE_NOT_ALLOWED);
    }

    private static boolean asciiEquals(byte[] data, int offset, String expected) {
        if (offset + expected.length() > data.length) return false;
        for (int i = 0; i < expected.length(); i++) {
            if ((byte) expected.charAt(i) != data[offset + i]) return false;
        }
        return true;
    }

    private static int littleEndian16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static int littleEndian24(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8) | ((data[offset + 2] & 0xff) << 16);
    }

    public static String extension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported image type: " + mimeType);
        };
    }
}
