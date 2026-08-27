package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileReferenceDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileReferenceMapper extends BaseMapperX<FileReferenceDO> {

    default List<FileReferenceDO> selectByBusiness(String businessType, String businessId, String fieldName) {
        return selectList(new LambdaQueryWrapperX<FileReferenceDO>()
                .eq(FileReferenceDO::getBusinessType, businessType)
                .eq(FileReferenceDO::getBusinessId, businessId)
                .eq(FileReferenceDO::getFieldName, fieldName));
    }

    @Delete("DELETE FROM infra_file_reference WHERE business_type = #{businessType} AND business_id = #{businessId} AND field_name = #{fieldName}")
    int deletePhysicallyByBusiness(@Param("businessType") String businessType,
                                   @Param("businessId") String businessId,
                                   @Param("fieldName") String fieldName);

    @Select("SELECT COUNT(*) FROM infra_file_reference WHERE file_id = #{fileId} AND deleted = 0")
    long countActiveByFileId(@Param("fileId") Long fileId);
}
