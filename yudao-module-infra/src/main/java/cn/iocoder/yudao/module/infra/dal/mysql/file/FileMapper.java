package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件操作 Mapper
 *
 * @author 芋道源码
 */
@Mapper
public interface FileMapper extends BaseMapperX<FileDO> {

    default PageResult<FileDO> selectPage(FilePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FileDO>()
                .likeIfPresent(FileDO::getPath, reqVO.getPath())
                .likeIfPresent(FileDO::getType, reqVO.getType())
                .eqIfPresent(FileDO::getAssetKey, reqVO.getAssetKey())
                .eqIfPresent(FileDO::getAssetStatus, reqVO.getAssetStatus())
                .eqIfPresent(FileDO::getModerationStatus, reqVO.getModerationStatus())
                .eqIfPresent(FileDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(FileDO::getOwnerUserType, reqVO.getOwnerUserType())
                .betweenIfPresent(FileDO::getCreateTime, reqVO.getCreateTime())
                .isNull(Boolean.TRUE.equals(reqVO.getOriginalOnly()), FileDO::getOriginalFileId)
                .orderByDesc(FileDO::getId));
    }

    default FileDO selectLatestByConfigIdAndPath(Long configId, String path) {
        return selectLastOne(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getConfigId, configId)
                .eq(FileDO::getPath, path)
                .orderByAsc(FileDO::getId));
    }

    default FileDO selectOriginalByDedupKey(String dedupKey) {
        return selectOne(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getDedupKey, dedupKey)
                .isNull(FileDO::getOriginalFileId)
                .orderByAsc(FileDO::getId)
                .last("LIMIT 1"));
    }

    default List<FileDO> selectOriginalsByModerationStatus(Integer moderationStatus, int limit) {
        return selectList(new LambdaQueryWrapperX<FileDO>()
                .eqIfPresent(FileDO::getModerationStatus, moderationStatus)
                .isNull(FileDO::getOriginalFileId)
                .orderByDesc(FileDO::getId)
                .last("LIMIT " + limit));
    }

    default FileDO selectByAssetKey(String assetKey) {
        return selectOne(FileDO::getAssetKey, assetKey);
    }

    default FileDO selectVariant(Long originalFileId, String variantName) {
        return selectOne(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getOriginalFileId, originalFileId)
                .eq(FileDO::getVariantName, variantName)
                .eq(FileDO::getAssetStatus, 10)
                .orderByDesc(FileDO::getId)
                .last("LIMIT 1"));
    }

    default FileDO selectVariantForManagement(Long originalFileId, String variantName) {
        return selectOne(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getOriginalFileId, originalFileId)
                .eq(FileDO::getVariantName, variantName)
                .orderByDesc(FileDO::getId)
                .last("LIMIT 1"));
    }

    default void sampleAccessTime(Long id, LocalDateTime cutoff, LocalDateTime now) {
        update(new FileDO().setLastAccessTime(now), new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getId, id)
                .and(wrapper -> wrapper.isNull(FileDO::getLastAccessTime)
                        .or().lt(FileDO::getLastAccessTime, cutoff)));
    }

    default FileDO selectByUrl(String url) {
        return selectOne(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getUrl, url).orderByDesc(FileDO::getId).last("LIMIT 1"));
    }

    default List<FileDO> selectOrphanCandidates(LocalDateTime unboundCutoff,
                                                 LocalDateTime replacedCutoff, int limit) {
        return selectList(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getAssetStatus, 10)
                .isNull(FileDO::getOriginalFileId)
                .isNotNull(FileDO::getOrphanedAt)
                .and(wrapper -> wrapper
                        .and(unbound -> unbound.eq(FileDO::getBoundOnce, false)
                                .le(FileDO::getOrphanedAt, unboundCutoff))
                        .or(replaced -> replaced.eq(FileDO::getBoundOnce, true)
                                .le(FileDO::getOrphanedAt, replacedCutoff)))
                .orderByAsc(FileDO::getOrphanedAt)
                .last("LIMIT " + limit));
    }

    default List<FileDO> selectVariants(Long originalFileId) {
        return selectList(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getOriginalFileId, originalFileId));
    }

    default void markBound(Long id) {
        update(null, Wrappers.<FileDO>lambdaUpdate()
                .eq(FileDO::getId, id)
                .set(FileDO::getBoundOnce, true)
                .set(FileDO::getOrphanedAt, null));
    }

    default void markOrphan(Long id, LocalDateTime now) {
        update(null, Wrappers.<FileDO>lambdaUpdate()
                .eq(FileDO::getId, id)
                .set(FileDO::getOrphanedAt, now));
    }

    @Delete("DELETE FROM infra_file WHERE id = #{id}")
    int deletePhysicallyById(@Param("id") Long id);

}
