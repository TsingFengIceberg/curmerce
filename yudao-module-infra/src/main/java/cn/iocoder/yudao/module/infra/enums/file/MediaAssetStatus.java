package cn.iocoder.yudao.module.infra.enums.file;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MediaAssetStatus {

    PROCESSING(0),
    READY(10),
    QUARANTINED(20),
    FAILED(30);

    private final int status;
}
