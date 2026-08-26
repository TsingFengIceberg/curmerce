package cn.iocoder.yudao.module.commerce.controller.admin.release.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReleaseUpdateReqVO extends ReleaseCreateReqVO {

    @NotNull
    private Long id;

}
