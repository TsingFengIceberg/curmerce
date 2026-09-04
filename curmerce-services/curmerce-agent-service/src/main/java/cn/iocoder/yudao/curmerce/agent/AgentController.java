package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestController
@RequestMapping("/app-api/agent")
public class AgentController {

    @Resource private AgentRetrievalService retrievalService;
    @Resource private AgentChatModel modelClient;
    @Resource private AgentUsageRecorder usageRecorder;
    @Resource private AgentToolRegistry toolRegistry;
    @Resource private AgentAuditRecorder auditRecorder;
    @Resource private AgentServiceProperties properties;
    @Resource private AgentProviderHealthService providerHealth;

    @GetMapping("/capabilities")
    public CommonResult<Map<String, Object>> capabilities() {
        return success(Map.of(
                "readOnlyByDefault", true,
                "sensitiveActionsRequireConfirmation", true,
                "modelBacked", modelClient.enabled(),
                "capabilities", List.of("product-discovery", "community-experience-retrieval", "order-status",
                        "authorized-read-only-tools", "confirmation-tokens", "source-degradation", "conversation-memory",
                        "vector-knowledge", "tool-registry", "policy-quota", "tool-result-loop",
                        "grounding-warnings", "usage-summary")
        ));
    }

    @GetMapping("/model/status")
    public CommonResult<Map<String, Object>> modelStatus() {
        return success(Map.of("enabled", modelClient.enabled(),
                "model", properties.modelName(), "embeddingEnabled", properties.embeddingEnabled(),
                "dailyTokenLimit", properties.dailyTokenLimit(), "dailyCostLimit", properties.dailyCostLimit()));
    }

    @GetMapping("/model/readiness")
    public CommonResult<Map<String, Object>> modelReadiness(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        if (!AgentInternalAuthorizer.matches(properties.internalToken(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅允许内部模型就绪检查调用");
        }
        Map<String, Object> readiness = new java.util.LinkedHashMap<>(providerHealth.check());
        readiness.put("circuit", retrievalService.modelCircuitState());
        return success(Map.copyOf(readiness));
    }

    /**
     * Performs one bounded real chat request without returning provider text.
     * This distinguishes a reachable model registry from an actually usable
     * chat provider and is intentionally internal-only because it consumes
     * provider quota.
     */
    @PostMapping("/model/smoke")
    public CommonResult<Map<String, Object>> modelSmoke(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token, "仅允许内部模型探针调用");
        if (!modelClient.enabled()) {
            return success(Map.of("enabled", false, "ready", false, "reason", "disabled"));
        }
        long started = System.nanoTime();
        try (AgentRequestContext.Scope ignored = AgentRequestContext.open("internal-model-smoke", AgentRequestContext.tenantId())) {
            SpringAiCompatibleChatClient.ModelAnswer answer = retrievalService.modelSmoke();
            return success(Map.of("enabled", true, "ready", true,
                    "answerReceived", answer != null && answer.answer() != null && !answer.answer().isBlank(),
                    "responseChars", answer == null || answer.answer() == null ? 0 : answer.answer().length(),
                    "toolCalls", answer == null || answer.toolCalls() == null ? 0 : answer.toolCalls().size(),
                    "latencyMs", java.time.Duration.ofNanos(System.nanoTime() - started).toMillis()));
        } catch (RuntimeException ex) {
            return success(Map.of("enabled", true, "ready", false, "reason", "unavailable",
                    "latencyMs", java.time.Duration.ofNanos(System.nanoTime() - started).toMillis()));
        }
    }

    @GetMapping("/model/circuit")
    public CommonResult<Map<String, Object>> modelCircuit(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token, "仅允许内部模型断路器查询调用");
        return success(retrievalService.modelCircuitState());
    }

    @PostMapping("/assist")
    public CommonResult<AgentAssistRespDTO> assist(@Valid @RequestBody AgentAssistReqDTO request,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        return success(retrievalService.assist(request.getQuery().trim(), authorization, request.getConversationId()));
    }

    @GetMapping("/usage/latest")
    public CommonResult<AgentUsageRecorder.Usage> latestUsage(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token, "仅允许内部用量查询调用");
        return success(usageRecorder.latest());
    }

    @GetMapping("/usage/summary")
    public CommonResult<AgentUsageRecorder.Summary> usageSummary(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token, "仅允许内部用量查询调用");
        return success(usageRecorder.summary());
    }

    @GetMapping("/usage/report")
    public CommonResult<AgentUsageJdbcArchive.Report> usageReport(
            @RequestParam(defaultValue = "7") int days,
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token, "仅允许内部用量报表调用");
        int safeDays = Math.min(3650, Math.max(1, days));
        java.time.Instant to = java.time.Instant.now();
        return success(usageRecorder.report(to.minus(java.time.Duration.ofDays(safeDays)), to));
    }

    @GetMapping("/usage/scopes")
    public CommonResult<Map<String, AgentUsageRecorder.Summary>> usageScopes(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token, "仅允许内部用量报表调用");
        return success(usageRecorder.scopeSummaries());
    }

    @GetMapping("/tools/registry")
    public CommonResult<List<AgentToolRegistry.ToolDescriptor>> toolRegistry() { return success(toolRegistry.list()); }

    @GetMapping("/audit/recent")
    public CommonResult<List<AgentAuditRecorder.Entry>> recentAudit(@RequestParam(defaultValue = "20") int limit,
                                                                    @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token, "仅允许内部审计调用");
        return success(auditRecorder.recent(limit));
    }

    private void requireInternal(String token, String message) {
        if (!AgentInternalAuthorizer.matches(properties.internalToken(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    @ExceptionHandler(AgentRetrievalService.AgentRateLimitException.class)
    void rateLimited(AgentRetrievalService.AgentRateLimitException ex) {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    void invalidRequest(IllegalArgumentException ex) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
}
