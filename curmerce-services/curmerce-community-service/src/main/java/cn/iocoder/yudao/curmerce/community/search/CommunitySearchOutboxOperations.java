package cn.iocoder.yudao.curmerce.community.search;

import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunitySearchOutboxMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "curmerce.search", name = "events-enabled", havingValue = "true")
public class CommunitySearchOutboxOperations {
    private final CommunitySearchOutboxMapper mapper;

    public CommunitySearchOutboxOperations(CommunitySearchOutboxMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public Map<Integer, Long> statusCounts() { return mapper.countByStatus(); }

    @Transactional(rollbackFor = Exception.class)
    public int requeueDead(int limit) {
        int count = 0;
        for (var event : mapper.selectDead(limit)) count += mapper.requeueDead(event.getId());
        return count;
    }
}
