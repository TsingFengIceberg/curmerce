package cn.iocoder.yudao.module.commerce.controller.app.catalog.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Length;

@Data
@EqualsAndHashCode(callSuper = true)
public class PublicProductPageReqVO extends PageParam {
    private Long categoryId;
    @Length(max = 64)
    private String keyword;
}
