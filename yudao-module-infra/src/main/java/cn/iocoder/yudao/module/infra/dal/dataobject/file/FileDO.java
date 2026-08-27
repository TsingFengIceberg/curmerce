package cn.iocoder.yudao.module.infra.dal.dataobject.file;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文件表
 * 每次文件上传，都会记录一条记录到该表中
 *
 * @author 芋道源码
 */
@TableName("infra_file")
@KeySequence("infra_file_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class FileDO extends BaseDO {

    /**
     * 编号，数据库自增
     */
    private Long id;
    /** 稳定且不可枚举的资产标识 */
    private String assetKey;
    /**
     * 配置编号
     *
     * 关联 {@link FileConfigDO#getId()}
     */
    private Long configId;
    /**
     * 原文件名
     */
    private String name;
    /**
     * 路径，即文件名
     */
    private String path;
    /**
     * 访问地址
     */
    private String url;
    /**
     * 文件的 MIME 类型，例如 "application/octet-stream"
     */
    private String type;
    /**
     * 文件大小
     */
    private Long size;
    /** 内容哈希，用于幂等上传和内容去重 */
    private String sha256;
    /** 同一所有者和可见性范围内的并发去重键；衍生图为空 */
    private String dedupKey;
    /** 0 处理中，10 可用，20 隔离，30 失败 */
    private Integer assetStatus;
    /** 0 待扫描，10 通过，20 拒绝，30 跳过 */
    private Integer scanStatus;
    /** 0 待审核，10 安全，20 人工复核，30 拒绝，40 异常，50 跳过 */
    private Integer moderationStatus;
    private String moderationReason;
    private Long moderatedBy;
    private LocalDateTime moderatedAt;
    /** 0 公开，10 私有 */
    private Integer visibility;
    private Long ownerUserId;
    private Integer ownerUserType;
    private Integer width;
    private Integer height;
    private Long originalFileId;
    private String variantName;
    private Boolean boundOnce;
    private LocalDateTime orphanedAt;
    private LocalDateTime lastAccessTime;
    private String failureReason;

}
