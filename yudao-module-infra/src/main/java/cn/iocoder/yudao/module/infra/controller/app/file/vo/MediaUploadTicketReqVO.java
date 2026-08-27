package cn.iocoder.yudao.module.infra.controller.app.file.vo;

import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadReqVO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "媒体预签名直传票据 Request VO")
@Data
public class MediaUploadTicketReqVO {
    @NotBlank
    @Size(max = 256)
    private String name;
    private String directory;
    @NotBlank
    private String contentType;
    @NotNull
    @Positive
    private Long size;
    @Min(0)
    @Max(10)
    @NotNull
    private Integer visibility = 0;

    @AssertTrue(message = "文件目录不正确")
    @JsonIgnore
    public boolean isDirectoryValid() {
        return FileUploadReqVO.isDirectoryValid(directory);
    }

    @AssertTrue(message = "媒体可见性不正确")
    @JsonIgnore
    public boolean isVisibilityValid() {
        return Integer.valueOf(0).equals(visibility) || Integer.valueOf(10).equals(visibility);
    }
}
