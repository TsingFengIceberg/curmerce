package cn.iocoder.yudao.module.infra.enums.file;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MediaModerationStatus {

    PENDING(0),
    SAFE(10),
    REVIEW_REQUIRED(20),
    REJECTED(30),
    ERROR(40),
    SKIPPED(50);

    private final int status;
}
