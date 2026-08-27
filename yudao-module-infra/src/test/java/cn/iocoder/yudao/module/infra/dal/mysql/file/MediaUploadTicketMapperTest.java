package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaUploadTicketDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.infra.enums.file.MediaUploadTicketStatus.ISSUED;
import static cn.iocoder.yudao.module.infra.enums.file.MediaUploadTicketStatus.PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaUploadTicketMapperTest extends BaseDbUnitTest {

    @Resource
    private MediaUploadTicketMapper mapper;

    @Test
    void selectAndClaimExpiredIncludesStaleProcessingTickets() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime staleBefore = now.minusMinutes(15);
        MediaUploadTicketDO expired = insertTicket("expired", ISSUED.getStatus(),
                now.minusMinutes(1), now.minusMinutes(1));
        MediaUploadTicketDO stale = insertTicket("stale", PROCESSING.getStatus(),
                now.plusMinutes(5), staleBefore.minusSeconds(1));
        MediaUploadTicketDO fresh = insertTicket("fresh", PROCESSING.getStatus(),
                now.plusMinutes(5), staleBefore.plusSeconds(1));

        List<MediaUploadTicketDO> selected = mapper.selectExpired(now, staleBefore, 100);

        assertEquals(List.of(expired.getId(), stale.getId()),
                selected.stream().map(MediaUploadTicketDO::getId).sorted().toList());
        assertEquals(1, mapper.claimExpired(expired.getId(), now, staleBefore));
        assertEquals(1, mapper.claimExpired(stale.getId(), now, staleBefore));
        assertEquals(0, mapper.claimExpired(fresh.getId(), now, staleBefore));
        assertEquals(PROCESSING.getStatus(), mapper.selectById(expired.getId()).getStatus());
        assertEquals(PROCESSING.getStatus(), mapper.selectById(stale.getId()).getStatus());
    }

    private MediaUploadTicketDO insertTicket(String key, int status, LocalDateTime expiresAt,
                                              LocalDateTime updateTime) {
        MediaUploadTicketDO ticket = new MediaUploadTicketDO().setTicketKey(key).setAssetKey("asset-" + key)
                .setConfigId(1L).setPath("uploads/" + key + ".png").setOriginalName(key + ".png")
                .setExpectedType("image/png").setExpectedSize(3L).setVisibility(0)
                .setOwnerUserId(7L).setOwnerUserType(1).setQuotaDate(LocalDate.now())
                .setStatus(status).setExpiresAt(expiresAt);
        ticket.setCreateTime(updateTime);
        ticket.setUpdateTime(updateTime);
        mapper.insert(ticket);
        return ticket;
    }
}
