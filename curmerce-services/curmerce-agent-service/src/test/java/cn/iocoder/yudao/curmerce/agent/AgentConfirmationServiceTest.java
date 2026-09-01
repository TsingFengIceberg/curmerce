package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConfirmationServiceTest {
    @Mock private AgentCoreClient coreClient;

    @Test
    void confirmationIsUserBoundAndOneTime() {
        when(coreClient.authenticate("user-token")).thenReturn(42L);
        AgentConfirmationService service = new AgentConfirmationService(coreClient);

        AgentConfirmationService.Issued issued = service.issue("user-token", "refund", "order-9");
        service.consume("user-token", issued.token(), "refund", "order-9");

        assertThatThrownBy(() -> service.consume("user-token", issued.token(), "refund", "order-9"))
                .isInstanceOf(AgentConfirmationService.AgentAuthorizationException.class);
    }

    @Test
    void failedBindingDoesNotInvalidateToken() {
        when(coreClient.authenticate("user-token")).thenReturn(42L);
        when(coreClient.authenticate("other-token")).thenReturn(43L);
        AgentConfirmationService service = new AgentConfirmationService(coreClient);

        AgentConfirmationService.Issued issued = service.issue("user-token", "refund", "order-9");
        assertThatThrownBy(() -> service.consume("other-token", issued.token(), "refund", "order-9"))
                .isInstanceOf(AgentConfirmationService.AgentAuthorizationException.class);
        service.consume("user-token", issued.token(), "refund", "order-9");
    }
}
