package cn.iocoder.yudao.module.community.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommunityMediaOutboxStatusEnum {
    PENDING(10),
    PROCESSING(20),
    SUCCEEDED(30);

    private final int status;
}
