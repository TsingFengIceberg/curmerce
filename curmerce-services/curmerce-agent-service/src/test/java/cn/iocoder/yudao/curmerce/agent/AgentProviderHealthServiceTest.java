package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AgentProviderHealthServiceTest {
    @Test
    void disabledProvidersReportExplicitDisabledReadiness() {
        AgentServiceProperties properties = new AgentServiceProperties("http://127.0.0.1:1",
                "http://127.0.0.1:2", java.time.Duration.ofMillis(20), java.time.Duration.ofMillis(20));
        var result = new AgentProviderHealthService(properties, new ObjectMapper()).check();
        var model = (java.util.Map<?, ?>) result.get("model");
        var embedding = (java.util.Map<?, ?>) result.get("embedding");
        assertEquals(false, model.get("enabled"));
        assertFalse((Boolean) model.get("ready"));
        assertEquals("disabled", model.get("reason"));
        assertEquals("disabled", embedding.get("reason"));
    }

    @Test
    void enabledProviderMustExposeTheConfiguredModel() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(200).when(response).statusCode();
        doReturn("{\"data\":[{\"id\":\"curmerce-local\"}]}").when(response).body();
        doReturn(response).when(client).send(any(), any());
        AgentServiceProperties properties = new AgentServiceProperties(
                "http://127.0.0.1:1", "http://127.0.0.1:2", java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(500),
                true, false, "http://127.0.0.1:12345", "", "curmerce-local", 0D, 0D, "internal",
                false, "http://127.0.0.1:2", "", "curmerce-embedding", 2, 12000,
                0L, 0D, false, "", "", "", 30, "redis", "http://127.0.0.1:3", "idx", "",
                false, "", "", "");

        var model = (java.util.Map<?, ?>) new AgentProviderHealthService(properties, new ObjectMapper(), client)
                .check().get("model");
        org.junit.jupiter.api.Assertions.assertEquals(true, model.get("ready"));
        org.junit.jupiter.api.Assertions.assertEquals(true, model.get("modelFound"));
    }

    @Test
    void readinessRetriesTransientProviderFailuresBeforeDeclaringUnavailable() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> unavailable = mock(HttpResponse.class);
        doReturn(503).when(unavailable).statusCode();
        doReturn("{\"error\":{\"message\":\"busy\"}}").when(unavailable).body();
        HttpResponse<String> ready = mock(HttpResponse.class);
        doReturn(200).when(ready).statusCode();
        doReturn("{\"data\":[{\"id\":\"curmerce-local\"}]}").when(ready).body();
        doReturn(unavailable, unavailable, ready, ready, ready).when(client).send(any(), any());
        AgentServiceProperties properties = new AgentServiceProperties(
                "http://127.0.0.1:1", "http://127.0.0.1:2", java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(500),
                true, false, "http://127.0.0.1:12345", "", "curmerce-local", 0D, 0D, "internal",
                false, "http://127.0.0.1:2", "", "curmerce-embedding", 2, 12000,
                0L, 0D, false, "", "", "", 30, "redis", "http://127.0.0.1:3", "idx", "",
                false, "", "", "");

        var model = (java.util.Map<?, ?>) new AgentProviderHealthService(properties, new ObjectMapper(), client)
                .check().get("model");
        assertEquals(true, model.get("ready"));
        verify(client, times(3)).send(any(), any());
    }
}
