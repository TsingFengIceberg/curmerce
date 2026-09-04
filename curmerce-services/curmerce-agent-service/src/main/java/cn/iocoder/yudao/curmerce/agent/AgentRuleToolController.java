package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Read-only logistics/refund/rule tools exposed through typed domain contracts. */
@RestController
@RequestMapping("/app-api/agent/tools")
public class AgentRuleToolController {
    private final AgentCoreClient core;
    private final AgentRuleCatalog ruleCatalog;
    public AgentRuleToolController(AgentCoreClient core, AgentRuleCatalog ruleCatalog) { this.core = core; this.ruleCatalog = ruleCatalog; }

    @GetMapping("/platform-rules")
    public CommonResult<Map<String, Object>> platformRules() {
        return success(ruleCatalog.current());
    }

    @GetMapping("/refund-status")
    public CommonResult<com.fasterxml.jackson.databind.JsonNode> refundStatus(@RequestParam Long orderId,
                                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        try { return success(core.getOwnRefundStatus(authorization, orderId)); }
        catch (AgentCoreClient.AgentAuthorizationException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex); }
        catch (AgentCoreClient.AgentServiceException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex); }
    }
}
