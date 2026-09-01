package cn.iocoder.yudao.curmerce.community.search;

import cn.iocoder.yudao.curmerce.community.integration.CoreServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local/internal search projection operations; no public user surface. */
@RestController
@ConditionalOnProperty(prefix = "curmerce.search", name = "events-enabled", havingValue = "true")
@RequestMapping("/internal/community/search-outbox")
public class CommunitySearchOutboxController {
    private final CommunitySearchOutboxOperations operations;
    private final CoreServiceProperties properties;

    public CommunitySearchOutboxController(CommunitySearchOutboxOperations operations, CoreServiceProperties properties) {
        this.operations = operations;
        this.properties = properties;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token,
                                      HttpServletRequest request) {
        authorize(token, request);
        return new LinkedHashMap<>(Map.of("statuses", operations.statusCounts(), "projection", "community-posts-to-elasticsearch"));
    }

    @PostMapping("/requeue-dead")
    public Map<String, Object> requeue(@RequestParam(defaultValue = "100") int limit,
                                       @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token,
                                       HttpServletRequest request) {
        authorize(token, request);
        return Map.of("requeued", operations.requeueDead(limit));
    }

    private void authorize(String token, HttpServletRequest request) {
        String expected = properties.internalToken();
        if (expected == null || expected.isBlank()) {
            String remote = request.getRemoteAddr();
            if (!"127.0.0.1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Community search operations are local-only");
            return;
        }
        if (token == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
    }
}
