package cn.iocoder.yudao.module.infra.service.file;

import java.time.LocalDate;

public record MediaQuotaReservation(Long userId, Integer userType, LocalDate quotaDate, long bytes) {
}
