package cn.iocoder.yudao.module.commerce.dal.dataobject.store;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("commerce_store")
@KeySequence("commerce_store_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreDO extends BaseDO {
    @TableId
    private Long id;
    private Long merchantId;
    private String name;
    private String code;
    private String description;
    private String contactName;
    private String contactMobile;
    private Integer status;
}
