package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.curmerce.cloud.api.CoreOrderStatusRespDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Typed, authenticated Agent tools; tools never access domain tables directly. */
@RestController
@RequestMapping("/app-api/agent")
public class AgentToolController {
    private final AgentCoreClient coreClient;
    private final AgentConfirmationService confirmationService;
    private final AgentToolExecutor executor;

    public AgentToolController(AgentCoreClient coreClient, AgentConfirmationService confirmationService, AgentToolExecutor executor) {
        this.coreClient = coreClient;
        this.confirmationService = confirmationService;
        this.executor = executor;
    }

    @PostMapping("/execute")
    public CommonResult<Object> execute(@Valid @RequestBody ToolExecuteRequest request,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            return success(executor.execute(authorization, request.tool(), request.arguments(), request.confirmationToken()));
        } catch (AgentToolExecutor.ToolConfirmationRequiredException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (AgentToolExecutor.ToolException | AgentConfirmationService.AgentAuthorizationException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (AgentCoreClient.AgentAuthorizationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        } catch (AgentCoreClient.AgentServiceException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
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
        try {
            return success(confirmationService.issue(authorization, request.action(), request.target()));
        } catch (AgentCoreClient.AgentAuthorizationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        } catch (AgentCoreClient.AgentServiceException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }
    }

    @PostMapping("/confirmations/consume")
    public CommonResult<Boolean> consumeConfirmation(@Valid @RequestBody ConfirmationConsumeRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            confirmationService.consume(authorization, request.token(), request.action(), request.target());
            return success(true);
        } catch (AgentCoreClient.AgentAuthorizationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        } catch (AgentCoreClient.AgentServiceException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (AgentConfirmationService.AgentAuthorizationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        }
    }

    public record ConfirmationIssueRequest(@NotBlank String action, @NotBlank String target) { }
    public record ConfirmationConsumeRequest(@NotBlank String token, @NotBlank String action, @NotBlank String target) { }
    public record ToolExecuteRequest(@NotBlank String tool, @NotNull JsonNode arguments, String confirmationToken) { }
}
