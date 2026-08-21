package cn.iocoder.yudao.module.commerce.controller.app.personal.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalListingUpdateReqVO extends PersonalListingCreateReqVO {
    @NotNull private Long id;
}
