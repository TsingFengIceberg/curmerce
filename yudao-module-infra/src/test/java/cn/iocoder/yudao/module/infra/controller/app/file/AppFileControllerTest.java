package cn.iocoder.yudao.module.infra.controller.app.file;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.infra.service.file.MediaAssetContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppFileControllerTest {

    private final AppFileController controller = new AppFileController();
    @Mock private FileService fileService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "fileService", fileService);
    }

    @Test
    void legacyAssetWithoutStoredHashGetsContentDerivedEtagAndSupportsNotModified() throws Exception {
        byte[] content = new byte[]{1, 2, 3};
        String assetKey = "74a8bdcf-570c-4b43-a9da-a69a87d126e5";
        FileDO file = new FileDO().setAssetKey(assetKey).setName(null).setType("image/png")
                .setVisibility(0).setSha256(null);
        when(fileService.getMediaAsset(assetKey, null)).thenReturn(new MediaAssetContent(file, content));
        String etag = '"' + DigestUtil.sha256Hex(content) + '"';

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        controller.getMediaAsset(new MockHttpServletRequest(), firstResponse, assetKey, null);
        assertEquals(etag, firstResponse.getHeader("ETag"));
        assertArrayEquals(content, firstResponse.getContentAsByteArray());
        assertTrue(firstResponse.getHeader("Content-Disposition").contains(assetKey));

        MockHttpServletRequest cachedRequest = new MockHttpServletRequest();
        cachedRequest.addHeader("If-None-Match", etag);
        MockHttpServletResponse cachedResponse = new MockHttpServletResponse();
        controller.getMediaAsset(cachedRequest, cachedResponse, assetKey, null);
        assertEquals(304, cachedResponse.getStatus());
        assertEquals(0, cachedResponse.getContentAsByteArray().length);
    }
}
