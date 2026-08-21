package cn.iocoder.yudao.module.commerce.dal.dataobject.release;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("commerce_release_campaign")
@KeySequence("commerce_release_campaign_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceReleaseCampaignDO extends BaseDO {
    @TableId private Long id;
    private Long merchantId;
    private Long storeId;
    private String name;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer perUserLimit;
}
