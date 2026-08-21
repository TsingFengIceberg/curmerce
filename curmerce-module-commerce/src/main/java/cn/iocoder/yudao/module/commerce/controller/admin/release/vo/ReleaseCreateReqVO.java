package cn.iocoder.yudao.module.commerce.controller.admin.release.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReleaseCreateReqVO {
    @NotBlank private String name;
    @NotNull @Future private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
    @NotNull @Min(1) private Integer perUserLimit;
    @NotEmpty @Valid private List<Item> items;

    @Data
    public static class Item {
        @NotNull private Long productId;
        @NotNull private Long skuId;
        @NotNull @Min(0) private Long campaignPrice;
        @NotNull @Min(1) private Integer stock;
    }
}
