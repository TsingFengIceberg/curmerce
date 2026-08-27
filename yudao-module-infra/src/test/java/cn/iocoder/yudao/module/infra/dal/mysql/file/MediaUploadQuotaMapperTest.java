package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaUploadQuotaDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaUploadQuotaMapperTest extends BaseDbUnitTest {

    @Resource
    private MediaUploadQuotaMapper mapper;

    @Test
    void atomicallyEnforcesCountAndByteLimits() {
        LocalDate date = LocalDate.of(2026, 8, 27);
        mapper.ensureQuotaRow(7L, 1, date);

        assertEquals(1, mapper.reserve(7L, 1, date, 400, 2, 1_000, 1_000));
        assertEquals(1, mapper.commitStorage(7L, 1, date, 400));
        assertEquals(1, mapper.reserve(7L, 1, date, 500, 2, 1_000, 1_000));
        assertEquals(0, mapper.reserve(7L, 1, date, 1, 2, 1_000, 1_000));

        MediaUploadQuotaDO quota = mapper.selectOne(MediaUploadQuotaDO::getOwnerUserId, 7L);
        assertEquals(2, quota.getUploadCount());
        assertEquals(900L, quota.getUploadBytes());
        assertEquals(500L, quota.getReservedStorageBytes());
    }

    @Test
    void releasesExpiredReservation() {
        LocalDate date = LocalDate.of(2026, 8, 27);
        mapper.ensureQuotaRow(8L, 2, date);
        mapper.reserve(8L, 2, date, 700, 2, 1_000, 1_000);

        assertEquals(1, mapper.release(8L, 2, date, 700));
        MediaUploadQuotaDO quota = mapper.selectOne(MediaUploadQuotaDO::getOwnerUserId, 8L);
        assertEquals(0, quota.getUploadCount());
        assertEquals(0L, quota.getUploadBytes());
        assertEquals(0L, quota.getReservedStorageBytes());
    }
}
