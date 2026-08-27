package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_IMAGE_DIMENSIONS_INVALID;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaImageInspectorTest {

    private final CurmerceMediaProperties properties = new CurmerceMediaProperties();
    private final MediaImageInspector inspector = new MediaImageInspector();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inspector, "properties", properties);
        properties.setMaxUploadSize(DataSize.ofMegabytes(1));
        properties.setMaxWidth(100);
        properties.setMaxHeight(100);
        properties.setMaxPixels(10_000);
    }

    @Test
    void acceptsRealPngAndReadsDimensions() throws Exception {
        MediaImageMetadata result = inspector.inspect(png(4, 3), "anything.jpg");
        assertEquals("image/png", result.mimeType());
        assertEquals("png", result.extension());
        assertEquals(4, result.width());
        assertEquals(3, result.height());
    }

    @Test
    void rejectsExtensionSpoofing() {
        assertServiceException(() -> inspector.inspect("not an image".getBytes(), "fake.png"),
                FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void rejectsExcessiveDimensions() throws Exception {
        properties.setMaxWidth(2);
        assertServiceException(() -> inspector.inspect(png(4, 3), "large.png"),
                FILE_IMAGE_DIMENSIONS_INVALID, 2, 100, 10_000);
    }

    @Test
    void webpWriterIsAvailableAtRuntime() {
        org.junit.jupiter.api.Assertions.assertTrue(ImageIO.getImageWritersByMIMEType("image/webp").hasNext());
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
