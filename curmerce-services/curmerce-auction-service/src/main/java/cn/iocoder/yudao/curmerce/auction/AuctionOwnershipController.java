package cn.iocoder.yudao.curmerce.auction;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Internal, token-protected cutover verification endpoint. */
@RestController
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
@RequestMapping("/internal/auction/ownership")
public class AuctionOwnershipController {
    private final AuctionOwnershipReconciliationService reconciliation;
    private final AuctionServiceProperties properties;

    public AuctionOwnershipController(AuctionOwnershipReconciliationService reconciliation,
                                       AuctionServiceProperties properties) {
        this.reconciliation = reconciliation;
        this.properties = properties;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token,
                                      HttpServletRequest request) {
        authorize(token, request);
        return reconciliation.verify();
    }

    private void authorize(String token, HttpServletRequest request) {
        String expected = properties.coreInternalToken();
        if (expected == null || expected.isBlank()) {
            String remote = request.getRemoteAddr();
            if (!"127.0.0.1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Auction ownership verification is local-only");
            }
            return;
        }
        if (token == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
    }
}
