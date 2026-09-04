package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/app-api/agent/evaluation")
public class AgentEvaluationController {
    private final AgentEvaluationService evaluation;
    private final AgentServiceProperties properties;
    public AgentEvaluationController(AgentEvaluationService evaluation, AgentServiceProperties properties) {
        this.evaluation = evaluation; this.properties = properties;
    }
    @GetMapping("/cases")
    public CommonResult<Map<String, Object>> cases(@RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        authorize(token);
        return success(Map.of("cases", evaluation.cases(), "policySmokePassed", evaluation.policyPasses(),
                "groundingSmokePassed", evaluation.groundingPasses(),
                "toolContractSmokePassed", evaluation.toolContractPasses(),
                "sensitiveToolContractSmokePassed", evaluation.sensitiveToolContractPasses()));
    }

    @PostMapping("/run")
    public CommonResult<AgentEvaluationService.EvaluationReport> run(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token,
            @RequestBody EvaluationRequest request) {
        authorize(token);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评测请求不能为空");
        return success(evaluation.evaluate(request.answer(), request.evidence()));
    }

    @PostMapping("/run-suite")
    public CommonResult<AgentEvaluationService.SuiteReport> runSuite(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        authorize(token);
        return success(evaluation.runSuite());
    }

    public record EvaluationRequest(String answer, String evidence) { }

    private void authorize(String token) {
        if (!AgentInternalAuthorizer.matches(properties.internalToken(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅允许内部评测调用");
        }
    }
}
