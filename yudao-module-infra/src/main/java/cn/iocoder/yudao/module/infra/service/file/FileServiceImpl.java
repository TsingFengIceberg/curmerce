package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;

import static cn.hutool.core.date.DatePattern.PURE_DATE_PATTERN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_ASSET_FORBIDDEN;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_ASSET_NOT_READY;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_SCAN_REJECTED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_SCAN_UNAVAILABLE;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_ASSET_IN_USE;
import static cn.iocoder.yudao.module.infra.enums.file.MediaAssetStatus.PROCESSING;
import static cn.iocoder.yudao.module.infra.enums.file.MediaAssetStatus.READY;
import static cn.iocoder.yudao.module.infra.enums.file.MediaModerationStatus.PENDING;
import static cn.iocoder.yudao.module.infra.enums.file.MediaModerationStatus.SKIPPED;

/**
 * 文件 Service 实现类
 *
 * @author 芋道源码
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Pattern STABLE_ASSET_URL = Pattern.compile(
            "^(?:https?://[^/]+)?/app-api/infra/file/assets/([0-9a-fA-F-]{36})(?:\\?.*)?$");

    /**
     * 上传文件的前缀，是否包含日期（yyyyMMdd）
     *
     * 目的：按照日期，进行分目录
     */
    static boolean PATH_PREFIX_DATE_ENABLE = true;
    /**
     * 上传文件的后缀，是否启用
     *
     * 算法：当前时间戳（毫秒）+ 5 位随机数；目的是保证文件的唯一性，避免覆盖
     * 定制：可按需调整成 UUID、或者其他方式
     */
    static boolean PATH_SUFFIX_TIMESTAMP_ENABLE = true;
    /**
     * 后缀是否作为上级目录
     *
     * true：{@code yyyyMMdd/<后缀>/原文件名.ext}；保留原文件名
     * false：{@code yyyyMMdd/原文件名_<后缀>.ext}；后缀拼到文件名
     */
    static boolean PATH_SUFFIX_AS_DIRECTORY = true;

    @Resource
    private FileConfigService fileConfigService;

    @Resource
    private FileMapper fileMapper;

    @Resource
    private FileReferenceMapper fileReferenceMapper;

    @Resource
    private MediaImageInspector mediaImageInspector;

    @Resource
    private MediaContentScanner mediaContentScanner;

    @Resource
    private CurmerceMediaProperties mediaProperties;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private MediaMetrics mediaMetrics;

    @Resource
    private MediaQuotaService mediaQuotaService;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO) {
        return fileMapper.selectPage(pageReqVO);
    }

    @Override
    @SneakyThrows
    public String createFile(byte[] content, String name, String directory, String type) {
        // 1.1 处理 name 的合法性，禁止携带目录路径
        name = FilePathUtils.validateFileName(name);

        // 1.2.1 处理 type 为空的情况
        if (StrUtil.isEmpty(type)) {
            type = FileTypeUtils.getMineType(content, name);
        }
        // 1.2.2 处理 name 为空的情况
        if (StrUtil.isEmpty(name)) {
            name = DigestUtil.sha256Hex(content);
        }
        if (StrUtil.isEmpty(FileUtil.extName(name))) {
            // 如果 name 没有后缀 type，则补充后缀
            String extension = FileTypeUtils.getExtension(type);
            if (StrUtil.isNotEmpty(extension)) {
                name = name + extension;
            }
        }

        // 2.1 生成上传的 path，需要保证唯一
        String path = generateUploadPath(name, directory);
        // 2.2 上传到文件存储器
        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        String url = client.upload(content, path, type);

        // 3. 保存到数据库
        fileMapper.insert(new FileDO().setAssetKey(UUID.randomUUID().toString()).setConfigId(client.getId())
                .setName(name).setPath(path).setUrl(url)
                .setType(type).setSize((long) content.length)
                .setAssetStatus(READY.getStatus()).setScanStatus(30)
                .setModerationStatus(SKIPPED.getStatus()).setVisibility(0).setBoundOnce(true));
        return url;
    }

    @Override
    @SneakyThrows
    public String createImage(byte[] content, String name, String directory) {
        name = FilePathUtils.validateFileName(name);
        FilePathUtils.validateDirectory(directory);
        MediaImageMetadata metadata;
        try {
            metadata = mediaImageInspector.inspect(content, name);
        } catch (RuntimeException ex) {
            mediaMetrics.rejected();
            throw ex;
        }
        MediaScanResult scan = mediaContentScanner.scan(content);
        if (scan.status() == MediaScanResult.Status.REJECTED) {
            mediaMetrics.rejected();
            throw exception(FILE_SCAN_REJECTED);
        }
        if (scan.status() == MediaScanResult.Status.ERROR) {
            mediaMetrics.rejected();
            throw exception(FILE_SCAN_UNAVAILABLE);
        }

        MediaQuotaReservation quota = mediaQuotaService.reserve(content.length);
        FileClient client = null;
        String path = null;
        boolean uploaded = false;
        boolean quotaSettled = false;
        try {
            String sha256 = DigestUtil.sha256Hex(content);
            String dedupKey = dedupKey(sha256, 0, quota.userId(), quota.userType());
            FileDO duplicate = fileMapper.selectOriginalByDedupKey(dedupKey);
            if (duplicate != null) {
                mediaQuotaService.commitStorage(quota);
                quotaSettled = true;
                return reuseDuplicate(duplicate, content.length);
            }

            String assetKey = UUID.randomUUID().toString();
            path = generateAssetPath(assetKey, metadata.extension(), directory);
            client = fileConfigService.getMasterFileClient();
            Assert.notNull(client, "客户端(master) 不能为空");
            String storageUrl = client.upload(content, path, metadata.mimeType());
            uploaded = true;

            FileDO file = new FileDO().setAssetKey(assetKey).setConfigId(client.getId())
                    .setName(name).setPath(path).setUrl(storageUrl)
                    .setType(metadata.mimeType()).setSize((long) content.length)
                    .setSha256(sha256).setDedupKey(dedupKey).setAssetStatus(PROCESSING.getStatus())
                    .setScanStatus(scan.status() == MediaScanResult.Status.CLEAN ? 10 : 30)
                    .setModerationStatus(PENDING.getStatus())
                    .setVisibility(0)
                    .setOwnerUserId(quota.userId()).setOwnerUserType(quota.userType())
                    .setWidth(metadata.width()).setHeight(metadata.height())
                    .setOrphanedAt(LocalDateTimeUtil.now());
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    fileMapper.insert(file);
                    mediaQuotaService.commitStorage(quota);
                    eventPublisher.publishEvent(new MediaAssetIngestedEvent(file.getId()));
                });
                quotaSettled = true;
            } catch (DuplicateKeyException ex) {
                deleteUploaded(client, path, ex);
                uploaded = false;
                FileDO concurrentDuplicate = fileMapper.selectOriginalByDedupKey(dedupKey);
                if (concurrentDuplicate == null) throw ex;
                mediaQuotaService.commitStorage(quota);
                quotaSettled = true;
                return reuseDuplicate(concurrentDuplicate, content.length);
            }
            mediaMetrics.stored(content.length);
            return stableAssetUrl(assetKey);
        } catch (Exception ex) {
            if (uploaded) deleteUploaded(client, path, ex);
            if (!quotaSettled) {
                try {
                    mediaQuotaService.release(quota);
                } catch (RuntimeException quotaError) {
                    ex.addSuppressed(quotaError);
                }
            }
            throw ex;
        }
    }

    private String reuseDuplicate(FileDO duplicate, int uploadedBytes) {
        if (!Integer.valueOf(PROCESSING.getStatus()).equals(duplicate.getAssetStatus())
                && !Integer.valueOf(10).equals(duplicate.getAssetStatus())) {
            throw exception(FILE_ASSET_NOT_READY);
        }
        mediaMetrics.deduplicated(uploadedBytes);
        return stableAssetUrl(duplicate.getAssetKey());
    }

    private static void deleteUploaded(FileClient client, String path, Exception original) {
        try {
            client.delete(path);
        } catch (Exception cleanupError) {
            original.addSuppressed(cleanupError);
        }
    }

    static String dedupKey(String sha256, int visibility, Long ownerUserId, Integer ownerUserType) {
        return sha256 + ":" + visibility + ":" + ownerUserType + ":" + ownerUserId;
    }

    @VisibleForTesting
    String generateAssetPath(String assetKey, String extension, String directory) {
        String path = LocalDateTimeUtil.format(LocalDateTimeUtil.now(), PURE_DATE_PATTERN)
                + StrUtil.SLASH + assetKey + StrUtil.DOT + extension;
        return StrUtil.isNotEmpty(directory) ? directory + StrUtil.SLASH + path : path;
    }

    public static String stableAssetUrl(String assetKey) {
        return "/app-api/infra/file/assets/" + assetKey;
    }

    @VisibleForTesting
    String generateUploadPath(String name, String directory) {
        // 1.1 处理 name 和 directory 的合法性
        name = FilePathUtils.validateFileName(name);
        FilePathUtils.validatePath(name);
        FilePathUtils.validateDirectory(directory);
        // 1.2 生成前缀、后缀
        String prefix = null;
        if (PATH_PREFIX_DATE_ENABLE) {
            prefix = LocalDateTimeUtil.format(LocalDateTimeUtil.now(), PURE_DATE_PATTERN);
        }
        String suffix = null;
        if (PATH_SUFFIX_TIMESTAMP_ENABLE) {
            // 5 位随机数，避免同一毫秒内的重复
            suffix = String.valueOf(System.currentTimeMillis()) + RandomUtil.randomInt(10000, 100000);
        }

        // 2.1 先拼接 suffix 后缀
        if (StrUtil.isNotEmpty(suffix)) {
            if (PATH_SUFFIX_AS_DIRECTORY) {
                name = suffix + StrUtil.SLASH + name;
            } else {
                String ext = FileUtil.extName(name);
                if (StrUtil.isNotEmpty(ext)) {
                    name = FileUtil.mainName(name) + StrUtil.C_UNDERLINE + suffix + StrUtil.DOT + ext;
                } else {
                    name = name + StrUtil.C_UNDERLINE + suffix;
                }
            }
        }
        // 2.2 再拼接 prefix 前缀
        if (StrUtil.isNotEmpty(prefix)) {
            name = prefix + StrUtil.SLASH + name;
        }
        // 2.3 最后拼接 directory 目录
        if (StrUtil.isNotEmpty(directory)) {
            name = directory + StrUtil.SLASH + name;
        }
        return name;
    }

    @Override
    @SneakyThrows
    public FilePresignedUrlRespVO presignPutUrl(String name, String directory) {
        // 1. 生成上传的 path，需要保证唯一
        String path = generateUploadPath(name, directory);

        // 2. 获取文件预签名地址
        FileClient fileClient = fileConfigService.getMasterFileClient();
        String uploadUrl = fileClient.presignPutUrl(path);
        String visitUrl = fileClient.presignGetUrl(path, null);
        return new FilePresignedUrlRespVO().setConfigId(fileClient.getId())
                .setPath(path).setUploadUrl(uploadUrl).setUrl(visitUrl);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        FileClient fileClient = fileConfigService.getMasterFileClient();
        return fileClient.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public Long createFile(FileCreateReqVO createReqVO) {
        // 1.1 校验参数的合法性
        FilePathUtils.validatePath(createReqVO.getPath());
        createReqVO.setName(FilePathUtils.validateFileName(createReqVO.getName()));
        // 1.2 处理 URL 的合法性，移除 URL 中的查询参数（例如签名参数），保证 URL 的唯一性
        createReqVO.setUrl(HttpUtils.removeUrlQuery(createReqVO.getUrl())); // 目的：移除私有桶情况下，URL 的签名参数

        // 2. 保存到数据库
        FileDO file = BeanUtils.toBean(createReqVO, FileDO.class);
        file.setAssetKey(UUID.randomUUID().toString()).setAssetStatus(READY.getStatus()).setScanStatus(30)
                .setModerationStatus(SKIPPED.getStatus()).setVisibility(0).setBoundOnce(true);
        fileMapper.insert(file);
        return file.getId();
    }

    @Override
    public FileDO getFile(Long id) {
        return validateFileExists(id);
    }

    @Override
    @SneakyThrows
    public MediaAssetContent getMediaAsset(String assetKey, String variantName) {
        FileDO original = fileMapper.selectByAssetKey(assetKey);
        validateDeliverableAsset(original);
        FileDO selected = StrUtil.isNotBlank(variantName)
                ? fileMapper.selectVariant(original.getId(), variantName) : original;
        if (selected == null) selected = original;
        return readMediaAsset(selected);
    }

    @Override
    @SneakyThrows
    public MediaAssetContent getManagedMediaAsset(Long id, String variantName) {
        FileDO requested = validateFileExists(id);
        FileDO original = requested.getOriginalFileId() == null
                ? requested : validateFileExists(requested.getOriginalFileId());
        FileDO selected = StrUtil.isNotBlank(variantName)
                ? fileMapper.selectVariantForManagement(original.getId(), variantName) : original;
        return readMediaAsset(selected != null ? selected : original);
    }

    private MediaAssetContent readMediaAsset(FileDO selected) throws Exception {
        FileClient client = fileConfigService.getFileClient(selected.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", selected.getConfigId());
        byte[] content = client.getContent(selected.getPath());
        if (content == null) throw exception(FILE_NOT_EXISTS);
        LocalDateTime now = LocalDateTimeUtil.now();
        fileMapper.sampleAccessTime(selected.getId(), now.minus(mediaProperties.getAccessTimestampInterval()), now);
        mediaMetrics.delivered();
        return new MediaAssetContent(selected, content);
    }

    @Override
    public void deleteFile(Long id) throws Exception {
        // 1.1 校验存在
        FileDO file = validateFileExists(id);
        if (fileReferenceMapper.countActiveByFileId(id) > 0) {
            throw exception(FILE_ASSET_IN_USE);
        }
        for (FileDO variant : fileMapper.selectVariants(id)) {
            deleteStoredFile(variant);
        }
        deleteStoredFile(file);
    }

    @Override
    @SneakyThrows
    public void deleteFileList(List<Long> ids) {
        List<FileDO> files = fileMapper.selectByIds(ids);
        for (FileDO file : files) {
            if (fileReferenceMapper.countActiveByFileId(file.getId()) > 0) {
                throw exception(FILE_ASSET_IN_USE);
            }
        }
        for (FileDO file : files) {
            for (FileDO variant : fileMapper.selectVariants(file.getId())) {
                deleteStoredFile(variant);
            }
            deleteStoredFile(file);
        }
    }

    private FileDO validateFileExists(Long id) {
        FileDO fileDO = fileMapper.selectById(id);
        if (fileDO == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        return fileDO;
    }

    @Override
    public byte[] getFileContent(Long configId, String path) throws Exception {
        // 1. 校验路径合法性
        FilePathUtils.validatePath(path);
        FileDO file = fileMapper.selectLatestByConfigIdAndPath(configId, path);
        if (file != null && file.getAssetKey() != null) {
            validateDeliverableAsset(file);
        }

        // 2.1 获取客户端
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, "客户端({}) 不能为空", configId);
        // 2.2 获取文件内容
        return client.getContent(path);
    }

    private void validateDeliverableAsset(FileDO file) {
        if (file == null || !Integer.valueOf(READY.getStatus()).equals(file.getAssetStatus())) {
            throw exception(FILE_ASSET_NOT_READY);
        }
        if (!Integer.valueOf(10).equals(file.getVisibility())) return;
        LoginUser user = getLoginUser();
        if (user == null || (!user.getId().equals(file.getOwnerUserId())
                && !Integer.valueOf(2).equals(user.getUserType()))) {
            throw exception(FILE_ASSET_FORBIDDEN);
        }
    }

    @Override
    public FileDO getFileByConfigIdAndPath(Long configId, String path) {
        return fileMapper.selectLatestByConfigIdAndPath(configId, path);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceFileReferences(String businessType, String businessId, String fieldName,
                                      Collection<String> urls) {
        Assert.notBlank(businessType, "businessType 不能为空");
        Assert.notBlank(businessId, "businessId 不能为空");
        Assert.notBlank(fieldName, "fieldName 不能为空");
        Set<Long> previousIds = fileReferenceMapper.selectByBusiness(businessType, businessId, fieldName)
                .stream().map(FileReferenceDO::getFileId).collect(java.util.stream.Collectors.toSet());
        Set<Long> nextIds = new LinkedHashSet<>();
        if (urls != null) {
            for (String url : urls) {
                FileDO file = resolveReferencedAsset(url);
                if (file != null) nextIds.add(file.getId());
            }
        }

        fileReferenceMapper.deletePhysicallyByBusiness(businessType, businessId, fieldName);
        for (Long fileId : nextIds) {
            fileReferenceMapper.insert(new FileReferenceDO().setFileId(fileId)
                    .setBusinessType(businessType).setBusinessId(businessId).setFieldName(fieldName));
            fileMapper.markBound(fileId);
        }
        LocalDateTime now = LocalDateTimeUtil.now();
        previousIds.stream().filter(id -> !nextIds.contains(id)).forEach(id -> {
            if (fileReferenceMapper.countActiveByFileId(id) == 0) fileMapper.markOrphan(id, now);
        });
    }

    private FileDO resolveReferencedAsset(String url) {
        if (StrUtil.isBlank(url)) return null;
        String normalized = url.trim();
        Matcher matcher = STABLE_ASSET_URL.matcher(normalized);
        FileDO file = matcher.matches() ? fileMapper.selectByAssetKey(matcher.group(1))
                : fileMapper.selectByUrl(HttpUtils.removeUrlQuery(normalized));
        if (file == null) return null; // External and bundled demo assets are not lifecycle-managed.
        boolean processing = Integer.valueOf(PROCESSING.getStatus()).equals(file.getAssetStatus());
        if (!processing && !Integer.valueOf(READY.getStatus()).equals(file.getAssetStatus())) {
            throw exception(FILE_ASSET_NOT_READY);
        }
        if (processing || Integer.valueOf(10).equals(file.getVisibility())) {
            LoginUser user = getLoginUser();
            if (user == null || (!user.getId().equals(file.getOwnerUserId())
                    && !Integer.valueOf(2).equals(user.getUserType()))) {
                throw exception(FILE_ASSET_FORBIDDEN);
            }
        }
        return file;
    }

    @Override
    @SneakyThrows
    public int cleanOrphanAssets() {
        LocalDateTime now = LocalDateTimeUtil.now();
        List<FileDO> candidates = fileMapper.selectOrphanCandidates(
                now.minus(mediaProperties.getUnboundRetention()),
                now.minus(mediaProperties.getReplacedRetention()), 100);
        int deleted = 0;
        for (FileDO original : candidates) {
            if (fileReferenceMapper.countActiveByFileId(original.getId()) > 0) {
                fileMapper.markBound(original.getId());
                continue;
            }
            for (FileDO variant : fileMapper.selectVariants(original.getId())) {
                deleteStoredFile(variant);
            }
            deleteStoredFile(original);
            deleted++;
        }
        mediaMetrics.orphansDeleted(deleted);
        return deleted;
    }

    private void deleteStoredFile(FileDO file) throws Exception {
        FilePathUtils.validatePath(file.getPath());
        FileClient client = fileConfigService.getFileClient(file.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", file.getConfigId());
        client.delete(file.getPath());
        fileMapper.deletePhysicallyById(file.getId());
    }

}
