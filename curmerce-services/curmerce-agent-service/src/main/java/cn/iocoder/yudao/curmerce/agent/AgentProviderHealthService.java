package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Performs an authenticated, bounded readiness probe for OpenAI-compatible providers. */
@Service
public class AgentProviderHealthService {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MILLIS = 50L;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final AgentServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    @Autowired
    public AgentProviderHealthService(AgentServiceProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    }

    AgentProviderHealthService(AgentServiceProperties properties, ObjectMapper objectMapper, HttpClient client) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    public Map<String, Object> check() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", probe(properties.modelEnabled(), properties.modelBaseUrl() + "/models",
                properties.modelApiKey(), properties.modelName()));
        result.put("embedding", probe(properties.embeddingEnabled(), properties.embeddingBaseUrl() + "/models",
                properties.embeddingApiKey(), properties.embeddingModel()));
        result.put("springAi", properties.springAiEnabled());
        return Map.copyOf(result);
    }

    private Map<String, Object> probe(boolean enabled, String endpoint, String apiKey, String expectedModel) {
        if (!enabled) return Map.of("enabled", false, "ready", false, "reason", "disabled");
        long started = System.nanoTime();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(endpoint))
                    .timeout(properties.readTimeout()).GET().header("Accept", "application/json");
            if (apiKey != null && !apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response = sendWithRetry(request.build());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            String detail = "";
            if (!ok && response.body() != null && !response.body().isBlank()) {
                try { detail = objectMapper.readTree(response.body()).path("error").path("message").asText(""); }
                catch (Exception ignored) { detail = ""; }
            }
            Map<String, Object> value = new LinkedHashMap<>();
            boolean modelFound = false;
            if (ok) {
                try {
                    JsonNode data = objectMapper.readTree(response.body()).path("data");
                    if (!data.isArray()) {
                        ok = false;
                        detail = "provider response does not contain a model list";
                    } else {
                        for (JsonNode item : data) {
                            if (expectedModel != null && expectedModel.equals(item.path("id").asText())) {
                                modelFound = true;
                                break;
                            }
                        }
                        if (!modelFound) {
                            ok = false;
                            detail = "configured model is not available";
                        }
                    }
                } catch (Exception ex) {
                    ok = false;
                    detail = "provider response is not valid JSON";
                }
            }
            value.put("enabled", true); value.put("ready", ok);
            value.put("model", expectedModel == null ? "" : expectedModel);
            value.put("modelFound", modelFound);
            value.put("status", response.statusCode());
            value.put("latencyMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
            if (!detail.isBlank()) value.put("detail", detail.substring(0, Math.min(300, detail.length())));
            return Map.copyOf(value);
        } catch (Exception ex) {
            return Map.of("enabled", true, "ready", false, "reason", "unreachable",
                    "latencyMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                response = client.send(request, AgentBoundedResponseBodyHandler.utf8(MAX_RESPONSE_BYTES));
                if (!retryable(response.statusCode()) || attempt == MAX_ATTEMPTS) return response;
            } catch (IOException ex) {
                if (attempt == MAX_ATTEMPTS) throw ex;
            }
            try {
                Thread.sleep(RETRY_BASE_DELAY_MILLIS * (1L << (attempt - 1)));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            }
        }
        return response;
    }

    private static boolean retryable(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }
}
