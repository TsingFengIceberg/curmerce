package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** App-facing feedback contract; it authenticates the author before recording a reaction. */
@RestController
@RequestMapping("/app-api/agent/feedback")
public class AgentFeedbackController {
    private final AgentCoreClient core;
    private final AgentFeedbackRecorder feedback;

    public AgentFeedbackController(AgentCoreClient core, AgentFeedbackRecorder feedback) {
        this.core = core;
        this.feedback = feedback;
    }

    @PostMapping
    public CommonResult<Boolean> record(@Valid @RequestBody FeedbackRequest request,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            Long userId = core.authenticate(authorization);
            feedback.record(userId, request.conversationId(), request.messageId(), request.helpful(), request.category());
            return success(true);
        } catch (AgentCoreClient.AgentAuthorizationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    void invalid(IllegalArgumentException ex) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }

    public record FeedbackRequest(@NotBlank String conversationId, @NotBlank String messageId,
                                  boolean helpful, String category) { }
}
