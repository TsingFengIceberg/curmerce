package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Internal versioned rule management; public Agent tools only read the current snapshot. */
@RestController
@RequestMapping("/internal-api/agent/rules")
public class AgentRuleAdminController {
    private final AgentRuleCatalog catalog;
    private final AgentServiceProperties properties;

    public AgentRuleAdminController(AgentRuleCatalog catalog, AgentServiceProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    @GetMapping
    public CommonResult<Map<String, Object>> get(@RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        authorize(token);
        return success(Map.of("version", catalog.version(), "rules", catalog.current()));
    }

    @GetMapping("/history")
    public CommonResult<java.util.List<AgentRuleJdbcStore.Revision>> history(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token,
            @RequestParam(defaultValue = "50") int limit) {
        authorize(token);
        return success(catalog.history(limit));
    }

    @PutMapping
    public CommonResult<Map<String, Object>> replace(@RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token,
                                                     @RequestBody RuleUpdate request) {
        authorize(token);
        if (request == null || request.rules() == null || request.rules().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "规则不能为空");
        }
        catalog.replace(request.rules(), request.version() == null ? 0L : request.version());
        return success(Map.of("version", catalog.version(), "rules", catalog.current()));
    }

    @PostMapping("/rollback")
    public CommonResult<Map<String, Object>> rollback(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token,
            @RequestBody RollbackRequest request) {
        authorize(token);
        if (request == null || request.targetVersion() == null || request.targetVersion() <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标规则版本必须为正数");
        }
        long version = catalog.rollback(request.targetVersion(), request.expectedCurrentVersion() == null
                ? 0L : request.expectedCurrentVersion());
        return success(Map.of("version", version, "rules", catalog.current()));
    }

    public record RuleUpdate(Long version, Map<String, Object> rules) { }
    public record RollbackRequest(Long targetVersion, Long expectedCurrentVersion) { }

    private void authorize(String token) {
        if (!AgentInternalAuthorizer.matches(properties.internalToken(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅允许内部规则管理调用");
        }
    }
}
