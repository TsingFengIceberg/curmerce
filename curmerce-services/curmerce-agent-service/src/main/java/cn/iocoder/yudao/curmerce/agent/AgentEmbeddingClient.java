package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Optional OpenAI-compatible embedding provider with a deterministic fallback in the store. */
@Component
public class AgentEmbeddingClient {
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MILLIS = 50L;
    private final AgentServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentEmbeddingClient(AgentServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public Optional<double[]> embed(String text) {
        if (!properties.embeddingEnabled() || text == null || text.isBlank()) return Optional.empty();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.embeddingBaseUrl() + "/embeddings"))
                    .timeout(properties.readTimeout())
                    .header("Content-Type", "application/json");
            if (!properties.embeddingApiKey().isBlank()) {
                request.header("Authorization", "Bearer " + properties.embeddingApiKey());
            }
            String payload = objectMapper.writeValueAsString(Map.of("model", properties.embeddingModel(), "input", text));
            HttpRequest httpRequest = request.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    response = httpClient.send(httpRequest, AgentBoundedResponseBodyHandler.utf8(MAX_RESPONSE_BYTES));
                    if (!retryable(response.statusCode()) || attempt == MAX_ATTEMPTS) break;
                } catch (java.io.IOException ex) {
                    if (attempt == MAX_ATTEMPTS) throw ex;
                }
                try {
                    Thread.sleep(RETRY_BASE_DELAY_MILLIS * (1L << (attempt - 1)));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
            if (response == null || response.statusCode() >= 300 || response.body() == null
                    || response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                return Optional.empty();
            }
            JsonNode values = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
            if (!values.isArray() || values.size() == 0) return Optional.empty();
            double[] vector = new double[values.size()];
            for (int i = 0; i < vector.length; i++) vector[i] = values.get(i).asDouble();
            return Optional.of(normalize(vector));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** Whether a real embedding provider is required for knowledge writes. */
    public boolean enabled() { return properties.embeddingEnabled(); }

    private static boolean retryable(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static double[] normalize(double[] vector) {
        double norm = 0D;
        for (double value : vector) norm += value * value;
        norm = Math.sqrt(norm);
        if (norm == 0D) return vector;
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        return vector;
    }
}
