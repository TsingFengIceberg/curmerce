package cn.iocoder.yudao.curmerce.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "curmerce.agent")
public record AgentServiceProperties(String coreBaseUrl, String communityBaseUrl,
                                     Duration connectTimeout, Duration readTimeout,
                                     boolean modelEnabled, String modelBaseUrl, String modelApiKey,
                                     String modelName, double inputCostPerThousandTokens,
                                     double outputCostPerThousandTokens, String internalToken) {
    public AgentServiceProperties(String coreBaseUrl, String communityBaseUrl,
                                  Duration connectTimeout, Duration readTimeout) {
        this(coreBaseUrl, communityBaseUrl, connectTimeout, readTimeout, false,
                "http://127.0.0.1:11434/v1", "", "curmerce-local", 0D, 0D, "");
    }

    public AgentServiceProperties {
        coreBaseUrl = coreBaseUrl == null ? "http://127.0.0.1:48080" : coreBaseUrl;
        communityBaseUrl = communityBaseUrl == null ? "http://127.0.0.1:48083" : communityBaseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
        modelBaseUrl = modelBaseUrl == null || modelBaseUrl.isBlank()
                ? "http://127.0.0.1:11434/v1" : modelBaseUrl.replaceAll("/$", "");
        modelApiKey = modelApiKey == null ? "" : modelApiKey;
        modelName = modelName == null || modelName.isBlank() ? "curmerce-local" : modelName;
        inputCostPerThousandTokens = Math.max(0D, inputCostPerThousandTokens);
        outputCostPerThousandTokens = Math.max(0D, outputCostPerThousandTokens);
        internalToken = internalToken == null ? "" : internalToken;
    }
}
