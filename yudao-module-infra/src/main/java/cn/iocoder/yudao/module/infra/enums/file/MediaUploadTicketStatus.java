package cn.iocoder.yudao.module.infra.enums.file;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MediaUploadTicketStatus {

    ISSUED(0),
    PROCESSING(5),
    FINALIZED(10),
    EXPIRED(20),
    REJECTED(30);

    private final int status;
}
