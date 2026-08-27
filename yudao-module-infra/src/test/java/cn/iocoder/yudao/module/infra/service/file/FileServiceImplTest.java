package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.test.core.util.AssertUtils;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildTime;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_PATH_INVALID;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_ASSET_FORBIDDEN;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_ASSET_NOT_READY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@Import({FileServiceImpl.class})
public class FileServiceImplTest extends BaseDbUnitTest {

    @Resource
    private FileServiceImpl fileService;

    @Resource
    private FileMapper fileMapper;

    @MockitoBean
    private FileConfigService fileConfigService;

    @Resource private FileReferenceMapper fileReferenceMapper;
    @MockitoBean private MediaImageInspector mediaImageInspector;
    @MockitoBean private MediaContentScanner mediaContentScanner;
    @MockitoBean private cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties mediaProperties;
    @MockitoBean private ApplicationEventPublisher eventPublisher;
    @MockitoBean private MediaMetrics mediaMetrics;
    @MockitoBean private MediaQuotaService mediaQuotaService;

    @BeforeEach
    public void setUp() {
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_AS_DIRECTORY = true;
        ReflectionTestUtils.setField(fileService, "eventPublisher", eventPublisher);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testGetFilePage() {
        // mock 数据
        FileDO dbFile = randomPojo(FileDO.class, o -> { // 等会查询到
            o.setPath("yunai");
            o.setType("image/jpg");
            validMediaStates(o);
            o.setCreateTime(buildTime(2021, 1, 15));
        });
        fileMapper.insert(dbFile);
        // 测试 path 不匹配
        fileMapper.insert(ObjectUtils.cloneIgnoreId(dbFile, o -> o.setPath("tudou")));
        // 测试 type 不匹配
        fileMapper.insert(ObjectUtils.cloneIgnoreId(dbFile, o -> {
            o.setType("image/png");
        }));
        // 测试 createTime 不匹配
        fileMapper.insert(ObjectUtils.cloneIgnoreId(dbFile, o -> {
            o.setCreateTime(buildTime(2020, 1, 15));
        }));
        // 准备参数
        FilePageReqVO reqVO = new FilePageReqVO();
        reqVO.setPath("yunai");
        reqVO.setType("jp");
        reqVO.setCreateTime((new LocalDateTime[]{buildTime(2021, 1, 10), buildTime(2021, 1, 20)}));

        // 调用
        PageResult<FileDO> pageResult = fileService.getFilePage(reqVO);
        // 断言
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        AssertUtils.assertPojoEquals(dbFile, pageResult.getList().get(0));
    }

    /**
     * content、name、directory、type 都非空
     */
    @Test
    public void testCreateFile_success_01() throws Exception {
        // 准备参数
        byte[] content = ResourceUtil.readBytes("file/erweima.jpg");
        String name = "单测文件名";
        String directory = randomString();
        String type = "image/jpeg";
        // mock Master 文件客户端
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        String url = randomString();
        AtomicReference<String> pathRef = new AtomicReference<>();
        when(client.upload(same(content), argThat(path -> {
            assertTrue(path.matches(directory + "/\\d{8}/\\d+/" + name + ".jpg"));
            pathRef.set(path);
            return true;
        }), eq(type))).thenReturn(url);
        when(client.getId()).thenReturn(10L);
        // 调用
        String result = fileService.createFile(content, name, directory, type);
        // 断言
        assertEquals(result, url);
        // 校验数据
        FileDO file = fileMapper.selectOne(FileDO::getUrl, url);
        assertEquals(10L, file.getConfigId());
        assertEquals(pathRef.get(), file.getPath());
        assertEquals(url, file.getUrl());
        assertEquals(type, file.getType());
        assertEquals(content.length, file.getSize());
    }

    /**
     * content 非空，其它都空
     */
    @Test
    public void testCreateFile_success_02() throws Exception {
        // 准备参数
        byte[] content = ResourceUtil.readBytes("file/erweima.jpg");
        // mock Master 文件客户端
        String type = "image/jpeg";
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        String url = randomString();
        AtomicReference<String> pathRef = new AtomicReference<>();
        when(client.upload(same(content), argThat(path -> {
            assertTrue(path.matches("\\d{8}/\\d+/6318848e882d8a7e7e82789d87608f684ee52d41966bfc8cad3ce15aad2b970e\\.jpg"));
            pathRef.set(path);
            return true;
        }), eq(type))).thenReturn(url);
        when(client.getId()).thenReturn(10L);
        // 调用
        String result = fileService.createFile(content, null, null, null);
        // 断言
        assertEquals(result, url);
        // 校验数据
        FileDO file = fileMapper.selectOne(FileDO::getUrl, url);
        assertEquals(10L, file.getConfigId());
        assertEquals(pathRef.get(), file.getPath());
        assertEquals(url, file.getUrl());
        assertEquals(type, file.getType());
        assertEquals(content.length, file.getSize());
    }

    @Test
    public void testDeleteFile_success() throws Exception {
        // mock 数据
        FileDO dbFile = randomPojo(FileDO.class, o -> {
            o.setConfigId(10L).setPath("tudou.jpg");
            validMediaStates(o);
        });
        fileMapper.insert(dbFile);// @Sql: 先插入出一条存在的数据
        // mock Master 文件客户端
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getFileClient(eq(10L))).thenReturn(client);
        // 准备参数
        Long id = dbFile.getId();

        // 调用
        fileService.deleteFile(id);
        // 校验数据不存在了
        assertNull(fileMapper.selectById(id));
        // 校验调用
        verify(client).delete(eq("tudou.jpg"));
    }

    @Test
    public void testDeleteFile_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用, 并断言异常
        assertServiceException(() -> fileService.deleteFile(id), FILE_NOT_EXISTS);
    }

    @Test
    public void testDeleteFile_pathInvalid() {
        // mock 数据
        FileDO dbFile = randomPojo(FileDO.class, o -> {
            o.setConfigId(10L).setPath("../tudou.jpg");
            validMediaStates(o);
        });
        fileMapper.insert(dbFile);

        // 调用，并断言异常
        assertServiceException(() -> fileService.deleteFile(dbFile.getId()), FILE_PATH_INVALID);
    }

    @Test
    public void testGetFileContent() throws Exception {
        // 准备参数
        Long configId = 10L;
        String path = "tudou.jpg";
        // mock 方法
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getFileClient(eq(10L))).thenReturn(client);
        byte[] content = new byte[]{};
        when(client.getContent(eq("tudou.jpg"))).thenReturn(content);

        // 调用
        byte[] result = fileService.getFileContent(configId, path);
        // 断言
        assertSame(result, content);
    }

    @Test
    public void testGetFileContent_pathInvalid() {
        // 准备参数
        Long configId = 10L;
        String path = "../tudou.jpg";

        // 调用，并断言异常
        assertServiceException(() -> fileService.getFileContent(configId, path), FILE_PATH_INVALID);
    }

    @Test
    public void testGetFileContent_blocksQuarantinedManagedAsset() {
        FileDO file = new FileDO().setAssetKey("34a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("quarantined.png").setPath("quarantine/test.png")
                .setUrl("stored").setType("image/png").setSize(3L).setAssetStatus(20)
                .setScanStatus(10).setModerationStatus(20).setVisibility(0).setBoundOnce(false);
        fileMapper.insert(file);

        assertServiceException(() -> fileService.getFileContent(10L, file.getPath()), FILE_ASSET_NOT_READY);
        verify(fileConfigService, never()).getFileClient(10L);
    }

    @Test
    public void testGetFileContent_blocksPrivateManagedAssetForAnonymousUser() {
        FileDO file = new FileDO().setAssetKey("44a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("private.png").setPath("private/direct.png")
                .setUrl("stored").setType("image/png").setSize(3L).setAssetStatus(10)
                .setScanStatus(10).setModerationStatus(10).setVisibility(10)
                .setOwnerUserId(77L).setOwnerUserType(1).setBoundOnce(false);
        fileMapper.insert(file);

        assertServiceException(() -> fileService.getFileContent(10L, file.getPath()), FILE_ASSET_FORBIDDEN);
        verify(fileConfigService, never()).getFileClient(10L);
    }

    @Test
    public void testGetPrivateAsset_requiresOwnerOrAdmin() {
        FileDO file = new FileDO().setAssetKey("14a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("private.png").setPath("private/test.png")
                .setUrl("stored").setType("image/png").setSize(3L).setSha256("abc")
                .setAssetStatus(10).setScanStatus(10).setModerationStatus(10)
                .setVisibility(10).setOwnerUserId(77L).setOwnerUserType(1).setBoundOnce(false);
        fileMapper.insert(file);

        assertServiceException(() -> fileService.getMediaAsset(file.getAssetKey(), null), FILE_ASSET_FORBIDDEN);
    }

    @Test
    public void testGetPrivateAsset_ownerCanRead() throws Exception {
        byte[] content = new byte[]{1, 2, 3};
        FileDO file = new FileDO().setAssetKey("24a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("private.png").setPath("private/test.png")
                .setUrl("stored").setType("image/png").setSize(3L).setSha256("abc")
                .setAssetStatus(10).setScanStatus(10).setModerationStatus(10)
                .setVisibility(10).setOwnerUserId(77L).setOwnerUserType(1).setBoundOnce(false);
        fileMapper.insert(file);
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getFileClient(10L)).thenReturn(client);
        when(client.getContent(file.getPath())).thenReturn(content);
        LoginUser loginUser = new LoginUser().setId(77L).setUserType(1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));

        assertArrayEquals(content, fileService.getMediaAsset(file.getAssetKey(), null).content());
    }

    @Test
    public void testGetManagedAsset_canPreviewQuarantinedVariant() throws Exception {
        byte[] content = new byte[]{4, 5, 6};
        FileDO original = new FileDO().setAssetKey("74a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("quarantined.png").setPath("quarantine/original.png")
                .setUrl("stored").setType("image/png").setSize(3L).setAssetStatus(20)
                .setScanStatus(10).setModerationStatus(20).setVisibility(0).setBoundOnce(false);
        fileMapper.insert(original);
        FileDO variant = new FileDO().setAssetKey("84a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("thumb.webp").setPath("quarantine/thumb.webp")
                .setUrl("stored-thumb").setType("image/webp").setSize(3L).setAssetStatus(20)
                .setScanStatus(10).setModerationStatus(20).setVisibility(0).setBoundOnce(true)
                .setOriginalFileId(original.getId()).setVariantName("thumb-webp");
        fileMapper.insert(variant);
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getFileClient(10L)).thenReturn(client);
        when(client.getContent(variant.getPath())).thenReturn(content);

        MediaAssetContent result = fileService.getManagedMediaAsset(original.getId(), "thumb-webp");

        assertEquals(variant.getId(), result.file().getId());
        assertArrayEquals(content, result.content());
    }

    @Test
    public void testReplaceReferences_allowsOwnerToBindProcessingAsset() {
        FileDO file = new FileDO().setAssetKey("94a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("processing.png").setPath("processing/test.png")
                .setUrl("stored").setType("image/png").setSize(3L).setAssetStatus(0)
                .setScanStatus(10).setModerationStatus(0).setVisibility(0)
                .setOwnerUserId(77L).setOwnerUserType(1).setBoundOnce(false);
        fileMapper.insert(file);
        LoginUser loginUser = new LoginUser().setId(77L).setUserType(1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));

        fileService.replaceFileReferences("community_post", "100", "media",
                List.of(FileServiceImpl.stableAssetUrl(file.getAssetKey())));

        assertEquals(1, fileReferenceMapper.countActiveByFileId(file.getId()));
        assertTrue(fileMapper.selectById(file.getId()).getBoundOnce());
    }

    @Test
    public void testCreateImage_reusesProcessingDuplicateWithoutUploading() throws Exception {
        byte[] content = new byte[]{1, 2, 3};
        MediaQuotaReservation reservation = new MediaQuotaReservation(77L, 1,
                java.time.LocalDate.of(2026, 8, 27), content.length);
        String dedupKey = FileServiceImpl.dedupKey(DigestUtil.sha256Hex(content), 0, 77L, 1);
        FileDO duplicate = new FileDO().setAssetKey("64a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("duplicate.png").setPath("duplicate/test.png")
                .setUrl("stored").setType("image/png").setSize(3L).setSha256(DigestUtil.sha256Hex(content))
                .setDedupKey(dedupKey).setAssetStatus(0).setScanStatus(10).setModerationStatus(0)
                .setVisibility(0).setOwnerUserId(77L).setOwnerUserType(1).setBoundOnce(false);
        fileMapper.insert(duplicate);
        when(mediaImageInspector.inspect(content, "test.png"))
                .thenReturn(new MediaImageMetadata("image/png", "png", 2, 2));
        when(mediaContentScanner.scan(content)).thenReturn(MediaScanResult.clean());
        when(mediaQuotaService.reserve(content.length)).thenReturn(reservation);

        assertEquals(FileServiceImpl.stableAssetUrl(duplicate.getAssetKey()),
                fileService.createImage(content, "test.png", "community"));

        verify(mediaQuotaService).commitStorage(reservation);
        verifyNoInteractions(fileConfigService);
        verify(mediaMetrics).deduplicated(content.length);
    }

    @Test
    public void testCreateImage_releasesQuotaWhenStorageFails() throws Exception {
        byte[] content = new byte[]{4, 5, 6};
        MediaQuotaReservation reservation = new MediaQuotaReservation(77L, 1,
                java.time.LocalDate.of(2026, 8, 27), content.length);
        FileClient client = mock(FileClient.class);
        when(mediaImageInspector.inspect(content, "test.png"))
                .thenReturn(new MediaImageMetadata("image/png", "png", 2, 2));
        when(mediaContentScanner.scan(content)).thenReturn(MediaScanResult.clean());
        when(mediaQuotaService.reserve(content.length)).thenReturn(reservation);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.upload(same(content), anyString(), eq("image/png")))
                .thenThrow(new IllegalStateException("storage unavailable"));

        assertThrows(IllegalStateException.class,
                () -> fileService.createImage(content, "test.png", "community"));

        verify(mediaQuotaService).release(reservation);
        verify(mediaQuotaService, never()).commitStorage(reservation);
    }

    @Test
    public void testCreateImage_persistsProcessingAssetAndPublishesEvent() throws Exception {
        byte[] content = new byte[]{7, 8, 9};
        MediaQuotaReservation reservation = new MediaQuotaReservation(77L, 1,
                java.time.LocalDate.of(2026, 8, 27), content.length);
        FileClient client = mock(FileClient.class);
        when(mediaImageInspector.inspect(content, "test.png"))
                .thenReturn(new MediaImageMetadata("image/png", "png", 2, 3));
        when(mediaContentScanner.scan(content)).thenReturn(MediaScanResult.clean());
        when(mediaQuotaService.reserve(content.length)).thenReturn(reservation);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.getId()).thenReturn(10L);
        when(client.upload(same(content), anyString(), eq("image/png"))).thenReturn("stored");

        String url = fileService.createImage(content, "test.png", "community");

        FileDO stored = fileMapper.selectOne(FileDO::getUrl, "stored");
        assertNotNull(stored);
        assertEquals(0, stored.getAssetStatus());
        assertEquals(2, stored.getWidth());
        assertEquals(3, stored.getHeight());
        assertEquals(url, FileServiceImpl.stableAssetUrl(stored.getAssetKey()));
        verify(mediaQuotaService).commitStorage(reservation);
        verify(eventPublisher).publishEvent(new MediaAssetIngestedEvent(stored.getId()));
        verify(mediaMetrics).stored(content.length);
    }

    @Test
    public void testReplaceReferences_marksDetachedAssetOrphaned() {
        FileDO file = new FileDO().setAssetKey("34a8bdcf-570c-4b43-a9da-a69a87d126e5")
                .setConfigId(10L).setName("product.png").setPath("product/test.png")
                .setUrl("stored").setType("image/png").setSize(3L).setSha256("def")
                .setAssetStatus(10).setScanStatus(10).setModerationStatus(10)
                .setVisibility(0).setOwnerUserId(77L).setOwnerUserType(1).setBoundOnce(false);
        fileMapper.insert(file);

        fileService.replaceFileReferences("product", "100", "images",
                List.of(FileServiceImpl.stableAssetUrl(file.getAssetKey())));
        assertEquals(1, fileReferenceMapper.selectList(FileReferenceDO::getFileId, file.getId()).size());
        assertTrue(fileMapper.selectById(file.getId()).getBoundOnce());

        fileService.replaceFileReferences("product", "100", "images", List.of());
        assertNotNull(fileMapper.selectById(file.getId()).getOrphanedAt());
    }

    @Test
    public void testGetFileByConfigIdAndPath() {
        // mock 数据
        FileDO dbFile = randomPojo(FileDO.class, o -> {
            o.setConfigId(10L).setPath("avatar/中文 100%+文件.jpg");
            validMediaStates(o);
        });
        fileMapper.insert(dbFile);
        FileDO latestFile = ObjectUtils.cloneIgnoreId(dbFile, o -> o.setName("最新文件名.jpg"));
        fileMapper.insert(latestFile);
        fileMapper.insert(ObjectUtils.cloneIgnoreId(dbFile, o -> o.setPath("avatar/other.jpg")));
        fileMapper.insert(ObjectUtils.cloneIgnoreId(dbFile, o -> o.setConfigId(20L)));

        // 调用
        FileDO result = fileService.getFileByConfigIdAndPath(10L, "avatar/中文 100%+文件.jpg");

        // 断言
        AssertUtils.assertPojoEquals(latestFile, result);
    }

    @Test
    public void testCreateFileByPresignedPath_success() {
        // 准备参数
        FileCreateReqVO reqVO = randomPojo(FileCreateReqVO.class, o -> {
            o.setPath("avatar/test.jpg");
            o.setName("test.jpg");
            o.setUrl("https://www.iocoder.cn/test.jpg?token=123");
        });

        // 调用
        Long fileId = fileService.createFile(reqVO);

        // 断言
        FileDO file = fileMapper.selectById(fileId);
        assertEquals("avatar/test.jpg", file.getPath());
        assertEquals("test.jpg", file.getName());
        assertEquals("https://www.iocoder.cn/test.jpg", file.getUrl());
    }

    @Test
    public void testCreateFileByPresignedPath_nameInvalid() {
        // 准备参数
        FileCreateReqVO reqVO = randomPojo(FileCreateReqVO.class, o -> {
            o.setPath("avatar/test.jpg");
            o.setName("../test.jpg");
        });

        // 调用，并断言异常
        assertServiceException(() -> fileService.createFile(reqVO), FILE_PATH_INVALID);
    }

    @Test
    public void testCreateFileByPresignedPath_pathInvalid() {
        // 准备参数
        FileCreateReqVO reqVO = randomPojo(FileCreateReqVO.class, o -> {
            o.setPath("../test.jpg");
            o.setName("test.jpg");
        });

        // 调用，并断言异常
        assertServiceException(() -> fileService.createFile(reqVO), FILE_PATH_INVALID);
    }

    @Test
    public void testGenerateUploadPath_AllEnabled() {
        // 准备参数
        String name = "test.jpg";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：avatar/yyyyMMdd/{时间戳+随机数}/test.jpg
        assertTrue(path.startsWith(directory + "/"));
        // 包含日期格式：8 位数字，如 20240517
        assertTrue(path.matches(directory + "/\\d{8}/\\d+/test\\.jpg"));
    }

    @Test
    public void testGenerateUploadPath_PrefixEnabled_SuffixDisabled() {
        // 准备参数
        String name = "test.jpg";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = false;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：avatar/yyyyMMdd/test.jpg
        assertTrue(path.startsWith(directory + "/"));
        // 包含日期格式：8 位数字，如 20240517
        assertTrue(path.matches(directory + "/\\d{8}/test\\.jpg"));
    }

    @Test
    public void testGenerateUploadPath_PrefixDisabled_SuffixEnabled() {
        // 准备参数
        String name = "test.jpg";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = false;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：avatar/{时间戳+随机数}/test.jpg
        assertTrue(path.startsWith(directory + "/"));
        assertTrue(path.matches(directory + "/\\d+/test\\.jpg"));
    }

    @Test
    public void testGenerateUploadPath_AllDisabled() {
        // 准备参数
        String name = "test.jpg";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = false;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = false;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：avatar/test.jpg
        assertEquals(directory + "/" + name, path);
    }

    @Test
    public void testGenerateUploadPath_NoExtension() {
        // 准备参数
        String name = "test";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：avatar/yyyyMMdd/{时间戳+随机数}/test
        assertTrue(path.startsWith(directory + "/"));
        assertTrue(path.matches(directory + "/\\d{8}/\\d+/test"));
    }

    private static void validMediaStates(FileDO file) {
        file.setAssetStatus(10).setScanStatus(10).setModerationStatus(50)
                .setVisibility(0).setOwnerUserType(1).setDedupKey(null)
                .setOriginalFileId(null).setVariantName(null);
    }

    @Test
    public void testGenerateUploadPath_DirectoryNull() {
        // 准备参数
        String name = "test.jpg";
        String directory = null;
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：yyyyMMdd/{时间戳+随机数}/test.jpg
        assertTrue(path.matches("\\d{8}/\\d+/test\\.jpg"));
    }

    @Test
    public void testGenerateUploadPath_SuffixAsName_AllEnabled() {
        // 准备参数
        String name = "test.jpg";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_AS_DIRECTORY = false;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：avatar/yyyyMMdd/test_{时间戳+随机数}.jpg
        assertTrue(path.matches(directory + "/\\d{8}/test_\\d+\\.jpg"));
    }

    @Test
    public void testGenerateUploadPath_SuffixAsName_PrefixDisabled() {
        // 准备参数
        String name = "test.jpg";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = false;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_AS_DIRECTORY = false;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：avatar/test_{时间戳+随机数}.jpg
        assertTrue(path.matches(directory + "/test_\\d+\\.jpg"));
    }

    @Test
    public void testGenerateUploadPath_SuffixAsName_NoExtension() {
        // 准备参数
        String name = "test";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_AS_DIRECTORY = false;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：avatar/yyyyMMdd/test_{时间戳+随机数}
        assertTrue(path.matches(directory + "/\\d{8}/test_\\d+"));
    }

    @Test
    public void testGenerateUploadPath_FileNameInvalid() {
        // 准备参数
        String name = "../test.jpg";
        String directory = "avatar";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = false;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = false;

        // 调用，并断言异常
        assertServiceException(() -> fileService.generateUploadPath(name, directory), FILE_PATH_INVALID);
    }

    @Test
    public void testGenerateUploadPath_DirectoryInvalid() {
        // 准备参数
        String name = "test.jpg";
        String directory = "../avatar";

        // 调用，并断言异常
        assertServiceException(() -> fileService.generateUploadPath(name, directory), FILE_PATH_INVALID);
    }

    @Test
    public void testGenerateUploadPath_DirectoryEmpty() {
        // 准备参数
        String name = "test.jpg";
        String directory = "";
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;

        // 调用
        String path = fileService.generateUploadPath(name, directory);

        // 断言
        // 格式为：yyyyMMdd/{时间戳+随机数}/test.jpg
        assertTrue(path.matches("\\d{8}/\\d+/test\\.jpg"));
    }

}
