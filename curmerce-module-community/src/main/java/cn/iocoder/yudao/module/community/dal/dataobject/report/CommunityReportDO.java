package cn.iocoder.yudao.module.community.dal.dataobject.report;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("community_report")
@KeySequence("community_report_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityReportDO extends BaseDO {
    @TableId private Long id;
    private Long postId;
    private Long reporterUserId;
    private String reason;
    private Integer status;
    private Long reviewerUserId;
    private String reviewRemark;
    private LocalDateTime reviewTime;
}
