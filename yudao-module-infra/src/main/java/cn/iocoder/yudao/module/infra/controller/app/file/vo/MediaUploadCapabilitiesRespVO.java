package cn.iocoder.yudao.module.infra.controller.app.file.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class MediaUploadCapabilitiesRespVO {
    private boolean directUpload;
    private long maxUploadBytes;
    private Set<String> allowedMimeTypes;
    private Set<String> variants;
}
