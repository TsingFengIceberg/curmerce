package cn.iocoder.yudao.module.member.dal.dataobject.address;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("member_address")
@KeySequence("member_address_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberAddressDO extends BaseDO {
    @TableId
    private Long id;
    private Long userId;
    private String name;
    private String mobile;
    private Integer areaId;
    private String detailAddress;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean defaultStatus;
    @TableField(select = true, updateStrategy = FieldStrategy.ALWAYS)
    private Integer defaultMarker;
}
