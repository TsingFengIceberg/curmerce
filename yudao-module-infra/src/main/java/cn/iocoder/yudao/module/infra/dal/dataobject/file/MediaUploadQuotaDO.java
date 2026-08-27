package cn.iocoder.yudao.module.infra.dal.dataobject.file;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@TableName("infra_media_upload_quota")
@KeySequence("infra_media_upload_quota_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadQuotaDO extends BaseDO {
    private Long id;
    private Long ownerUserId;
    private Integer ownerUserType;
    private LocalDate quotaDate;
    private Integer uploadCount;
    private Long uploadBytes;
    private Long reservedStorageBytes;
}
