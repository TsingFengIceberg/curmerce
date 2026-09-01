package cn.iocoder.yudao.curmerce.auction;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Public Auction API backed exclusively by the Auction-owned schema. */
@RestController
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
@RequestMapping({"/app-api/commerce/auction", "/admin-api/commerce/auction"})
public class AuctionOwnedController {
    private final AuctionOwnedService service;
    private final AuctionCoreClient core;

    public AuctionOwnedController(AuctionOwnedService service, AuctionCoreClient core) { this.service = service; this.core = core; }

    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int pageNo,
                                    @RequestParam(defaultValue = "10") int pageSize,
                                    @RequestParam(required = false) String keyword,
                                    HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/admin-api/")) {
            Long userId = serviceUser(request);
            var owner = core.merchantOwner(userId);
            if (owner == null) throw new AuctionOwnedService.AuctionBusinessException("当前用户没有已审核商家");
            return success(service.ownerPage(owner.getMerchantId(), keyword, pageNo, pageSize));
        }
        return success(service.publicPage(keyword, pageNo, pageSize));
    }

    @GetMapping("/get")
    public Map<String, Object> get(@RequestParam Long id, HttpServletRequest request) {
        boolean admin = request.getRequestURI().startsWith("/admin-api/");
        Long userId = admin ? serviceUser(request) : null;
        return success(service.get(id, admin, userId));
    }

    @GetMapping("/bid-page")
    public Map<String, Object> bidPage(@RequestParam Long sessionId,
                                       @RequestParam(defaultValue = "1") int pageNo,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       HttpServletRequest request) {
        Long userId = nullableUser(request);
        return success(service.bidPage(sessionId, userId, pageNo, pageSize));
    }

    @PostMapping("/bid")
    public Map<String, Object> bid(@RequestBody BidRequest body, HttpServletRequest request) {
        return success(service.bid(request.getHeader("Authorization"), body.sessionId(), body.amount(), body.idempotencyKey()));
    }

    @PostMapping("/settle")
    public Map<String, Object> settle(@RequestBody SettleRequest body, HttpServletRequest request) {
        return success(service.settle(request.getHeader("Authorization"), body.sessionId(), body.addressId()));
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody CreateRequest body, HttpServletRequest request) {
        Long userId = serviceUser(request);
        return success(service.create(userId, body.name(), body.productId(), body.skuId(), body.startingPrice(), body.minIncrement(), body.startTime(), body.endTime()));
    }

    @PutMapping("/update")
    public Map<String, Object> update(@RequestBody CreateRequest body, HttpServletRequest request) {
        Long userId = serviceUser(request);
        service.update(userId, body.id(), body.name(), body.productId(), body.skuId(), body.startingPrice(), body.minIncrement(), body.startTime(), body.endTime());
        return success(true);
    }

    @GetMapping("/own-page")
    public Map<String, Object> ownPage(@RequestParam(defaultValue = "1") int pageNo,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String keyword,
                                       HttpServletRequest request) {
        Long userId = serviceUser(request);
        var owner = core.merchantOwner(userId);
        if (owner == null) throw new AuctionOwnedService.AuctionBusinessException("当前用户没有已审核商家");
        return success(service.ownerPage(owner.getMerchantId(), keyword, pageNo, pageSize));
    }

    @PutMapping("/publish")
    public Map<String, Object> publish(@RequestParam Long id, HttpServletRequest request) { service.publish(serviceUser(request), id); return success(true); }
    @PutMapping("/cancel")
    public Map<String, Object> cancel(@RequestParam Long id, HttpServletRequest request) { service.cancel(serviceUser(request), id); return success(true); }
    @PutMapping("/end")
    public Map<String, Object> end(@RequestParam Long id, HttpServletRequest request) { service.end(serviceUser(request), id); return success(true); }

    private Long serviceUser(HttpServletRequest request) { return serviceUserFromAuth(request.getHeader("Authorization")); }
    private Long nullableUser(HttpServletRequest request) { String auth = request.getHeader("Authorization"); return auth == null || auth.isBlank() ? null : serviceUserFromAuth(auth); }
    private Long serviceUserFromAuth(String auth) { return core.authenticate(auth); }

    private static Map<String, Object> success(Object data) { Map<String, Object> result = new LinkedHashMap<>(); result.put("code", 0); result.put("msg", ""); result.put("data", data); return result; }

    public record BidRequest(Long sessionId, Long amount, String idempotencyKey) {}
    public record SettleRequest(Long sessionId, Long addressId) {}
    public record CreateRequest(Long id, String name, Long productId, Long skuId, Long startingPrice, Long minIncrement, LocalDateTime startTime, LocalDateTime endTime) {}

    @ExceptionHandler(AuctionCoreClient.AuctionCoreUnavailableException.class)
    ResponseEntity<Map<String, Object>> coreUnavailable(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(ex.getMessage()));
    }

    @ExceptionHandler(AuctionOwnedService.AuctionBusinessException.class)
    ResponseEntity<Map<String, Object>> businessError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    private static Map<String, Object> error(String message) { Map<String, Object> result = new LinkedHashMap<>(); result.put("code", 1); result.put("msg", message); result.put("data", null); return result; }
}
