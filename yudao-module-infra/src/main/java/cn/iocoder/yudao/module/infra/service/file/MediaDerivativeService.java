package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@Slf4j
public class MediaDerivativeService {

    private record Variant(String name, int maxEdge, int quality, String format) {}
    private static final Variant[] VARIANTS = {
            new Variant("thumb-webp", 256, 80, "webp"),
            new Variant("card-webp", 640, 84, "webp"),
            new Variant("thumb-avif", 256, 72, "avif"),
            new Variant("card-avif", 640, 76, "avif")
    };

    @Resource private FileMapper fileMapper;
    @Resource private FileConfigService fileConfigService;
    @Resource private CurmerceMediaProperties properties;
    @Resource private MediaMetrics metrics;
    @Resource private MediaImageInspector imageInspector;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReady(MediaAssetReadyEvent event) {
        if (!properties.isDerivativesEnabled()) return;
        FileDO original = fileMapper.selectById(event.fileId());
        if (original == null || !Integer.valueOf(10).equals(original.getAssetStatus())) return;
        try {
            FileClient client = fileConfigService.getFileClient(original.getConfigId());
            if (properties.getImgproxy().isEnabled()) {
                ensureDimensions(original, client);
                String sourceUrl = client.presignGetUrl(original.getPath(),
                        properties.getImgproxy().getSourceUrlTtlSeconds());
                for (Variant variant : VARIANTS) createImgproxyVariant(original, sourceUrl, variant, client);
            } else {
                byte[] source = client.getContent(original.getPath());
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
                if (image == null) throw new IllegalArgumentException("No ImageIO reader for " + original.getType());
                for (Variant variant : VARIANTS) {
                    if ("webp".equals(variant.format())) createJavaWebpVariant(original, image, variant, client);
                }
            }
        } catch (Exception ex) {
            metrics.variantFailed();
            fileMapper.updateById(new FileDO().setId(original.getId())
                    .setFailureReason("Derivative generation failed: " + safeMessage(ex)));
            log.warn("[onReady][media derivative generation failed for fileId={}]", event.fileId(), ex);
        }
    }

    private void ensureDimensions(FileDO original, FileClient client) throws Exception {
        if (original.getWidth() != null && original.getHeight() != null) return;
        MediaImageMetadata metadata = imageInspector.inspect(client.getContent(original.getPath()), original.getName());
        original.setWidth(metadata.width()).setHeight(metadata.height());
        fileMapper.updateById(new FileDO().setId(original.getId())
                .setWidth(metadata.width()).setHeight(metadata.height()));
    }

    private void createJavaWebpVariant(FileDO original, BufferedImage image, Variant variant,
                                       FileClient client) throws Exception {
        if (fileMapper.selectVariant(original.getId(), variant.name()) != null) return;
        double scale = Math.min(1D, (double) variant.maxEdge() / Math.max(image.getWidth(), image.getHeight()));
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        byte[] content = writeWebp(resized, variant.quality() / 100F);
        storeVariant(original, variant, client, content, width, height);
    }

    private void createImgproxyVariant(FileDO original, String sourceUrl, Variant variant,
                                       FileClient client) throws Exception {
        if (fileMapper.selectVariant(original.getId(), variant.name()) != null) return;
        CurmerceMediaProperties.Imgproxy config = properties.getImgproxy();
        if (config.getKeyHex() == null || config.getSaltHex() == null) {
            throw new IllegalStateException("imgproxy signing key and salt must be configured");
        }
        String encodedSource = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sourceUrl.getBytes(StandardCharsets.UTF_8));
        String path = "/rs:fit:" + variant.maxEdge() + ":" + variant.maxEdge() + ":0/q:"
                + variant.quality() + "/" + encodedSource + "." + variant.format();
        String signature = signImgproxyPath(path, config.getKeyHex(), config.getSaltHex());
        URI uri = URI.create(config.getEndpoint().replaceAll("/$", "") + "/" + signature + path);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(config.getRequestTimeout()).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(config.getRequestTimeout()).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("imgproxy returned HTTP " + response.statusCode());
        }
        double scale = Math.min(1D, (double) variant.maxEdge()
                / Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        storeVariant(original, variant, client, response.body(), width, height);
    }

    private void storeVariant(FileDO original, Variant variant, FileClient client,
                              byte[] content, int width, int height) throws Exception {
        String assetKey = UUID.randomUUID().toString();
        String mimeType = "image/" + variant.format();
        String path = "variants/" + original.getAssetKey() + "/" + variant.name()
                + "-" + assetKey + "." + variant.format();
        String url = client.upload(content, path, mimeType);
        try {
            fileMapper.insert(new FileDO().setAssetKey(assetKey).setConfigId(client.getId())
                    .setName(variant.name() + "." + variant.format()).setPath(path).setUrl(url)
                    .setType(mimeType).setSize((long) content.length)
                    .setSha256(DigestUtil.sha256Hex(content)).setAssetStatus(10).setScanStatus(10)
                    .setModerationStatus(original.getModerationStatus())
                    .setVisibility(original.getVisibility()).setOwnerUserId(original.getOwnerUserId())
                    .setOwnerUserType(original.getOwnerUserType()).setWidth(width).setHeight(height)
                    .setOriginalFileId(original.getId()).setVariantName(variant.name()).setBoundOnce(true));
            metrics.variantCreated();
        } catch (DuplicateKeyException ex) {
            client.delete(path); // Another worker won the same original/variant slot.
        } catch (RuntimeException ex) {
            client.delete(path);
            throw ex;
        }
    }

    private static String signImgproxyPath(String path, String keyHex, String saltHex) throws Exception {
        byte[] key = HexFormat.of().parseHex(keyHex);
        byte[] salt = HexFormat.of().parseHex(saltHex);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(salt);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(path.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] writeWebp(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) throw new IllegalStateException("WebP ImageIO writer is unavailable");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null) return ex.getClass().getSimpleName();
        return message.substring(0, Math.min(message.length(), 450));
    }
}
