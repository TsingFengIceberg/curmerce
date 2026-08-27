package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/app-api/agent")
public class AgentController {

    @Resource private AgentRetrievalService retrievalService;

    @GetMapping("/capabilities")
    public CommonResult<Map<String, Object>> capabilities() {
        return success(Map.of(
                "readOnly", true,
                "modelBacked", false,
                "capabilities", List.of("product-discovery", "community-experience-retrieval", "source-degradation")
        ));
    }

    @PostMapping("/assist")
    public CommonResult<AgentAssistRespDTO> assist(@Valid @RequestBody AgentAssistReqDTO request) {
        return success(retrievalService.assist(request.getQuery().trim()));
    }
}
