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
import java.time.LocalDateTime;

@TableName("infra_media_upload_ticket")
@KeySequence("infra_media_upload_ticket_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadTicketDO extends BaseDO {
    private Long id;
    private String ticketKey;
    private String assetKey;
    private Long configId;
    private String path;
    private String originalName;
    private String expectedType;
    private Long expectedSize;
    private String directory;
    private Integer visibility;
    private Long ownerUserId;
    private Integer ownerUserType;
    private LocalDate quotaDate;
    private Integer status;
    private LocalDateTime expiresAt;
    private Long finalizedFileId;
    private String failureReason;
}
