package cn.iocoder.yudao.module.commerce.controller.app.personal.vo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PersonalListingCreateReqVO {
    @NotNull private Long categoryId;
    @NotBlank @Size(min = 2, max = 128) private String name;
    @NotBlank @Size(min = 2, max = 32, message = "成色长度必须为 2-32 个字符") private String condition;
    @NotBlank @Size(max = 1024) private String mainImageUrl;
    @Size(max = 20) private List<@Size(max = 1024) String> imageUrls;
    @NotBlank @Size(min = 2, max = 100000) private String description;
    @NotNull @DecimalMin("0") private Long price;
}
