package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/app-api/agent")
public class AgentController {

    @Resource private AgentRetrievalService retrievalService;
    @Resource private SpringAiCompatibleChatClient modelClient;
    @Resource private AgentUsageRecorder usageRecorder;

    @GetMapping("/capabilities")
    public CommonResult<Map<String, Object>> capabilities() {
        return success(Map.of(
                "readOnly", true,
                "modelBacked", modelClient.enabled(),
                "capabilities", List.of("product-discovery", "community-experience-retrieval", "order-status",
                        "authorized-read-only-tools", "confirmation-tokens", "source-degradation")
        ));
    }

    @PostMapping("/assist")
    public CommonResult<AgentAssistRespDTO> assist(@Valid @RequestBody AgentAssistReqDTO request,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        return success(retrievalService.assist(request.getQuery().trim(), authorization));
    }

    @GetMapping("/usage/latest")
    public CommonResult<AgentUsageRecorder.Usage> latestUsage() {
        return success(usageRecorder.latest());
    }
}
