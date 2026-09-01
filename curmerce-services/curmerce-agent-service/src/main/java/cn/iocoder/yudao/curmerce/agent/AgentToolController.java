package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.curmerce.cloud.api.CoreOrderStatusRespDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Typed, authenticated Agent tools; tools never access domain tables directly. */
@RestController
@RequestMapping("/app-api/agent")
public class AgentToolController {
    private final AgentCoreClient coreClient;
    private final AgentConfirmationService confirmationService;

    public AgentToolController(AgentCoreClient coreClient, AgentConfirmationService confirmationService) {
        this.coreClient = coreClient;
        this.confirmationService = confirmationService;
    }

    @GetMapping("/tools/order-status")
    public CommonResult<CoreOrderStatusRespDTO> orderStatus(@RequestParam Long orderId,
                                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            return success(coreClient.getOwnOrderStatus(authorization, orderId));
        } catch (AgentCoreClient.AgentAuthorizationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        } catch (AgentCoreClient.AgentServiceException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }
    }

    @PostMapping("/confirmations")
    public CommonResult<AgentConfirmationService.Issued> issueConfirmation(@Valid @RequestBody ConfirmationIssueRequest request,
                                                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        return success(confirmationService.issue(authorization, request.action(), request.target()));
    }

    @PostMapping("/confirmations/consume")
    public CommonResult<Boolean> consumeConfirmation(@Valid @RequestBody ConfirmationConsumeRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            confirmationService.consume(authorization, request.token(), request.action(), request.target());
            return success(true);
        } catch (AgentConfirmationService.AgentAuthorizationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        }
    }

    public record ConfirmationIssueRequest(@NotBlank String action, @NotBlank String target) { }
    public record ConfirmationConsumeRequest(@NotBlank String token, @NotBlank String action, @NotBlank String target) { }
}
