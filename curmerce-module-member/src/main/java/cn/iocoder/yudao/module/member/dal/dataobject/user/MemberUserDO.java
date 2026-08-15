package cn.iocoder.yudao.module.member.dal.dataobject.user;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("member_user")
@KeySequence("member_user_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberUserDO extends BaseDO {
    @TableId
    private Long id;
    private String mobile;
    @JsonIgnore
    @TableField(select = true)
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private Integer sex;
    private Integer status;
    private String registerIp;
    private String loginIp;
    private LocalDateTime loginDate;
}
