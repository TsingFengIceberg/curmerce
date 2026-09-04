package cn.iocoder.yudao.curmerce.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "curmerce.agent")
public record AgentServiceProperties(String coreBaseUrl, String communityBaseUrl,
                                     Duration connectTimeout, Duration readTimeout,
                                     boolean modelEnabled, boolean springAiEnabled, String modelBaseUrl, String modelApiKey,
                                     String modelName, double inputCostPerThousandTokens,
                                     double outputCostPerThousandTokens, String internalToken,
                                     boolean embeddingEnabled, String embeddingBaseUrl,
                                     String embeddingApiKey, String embeddingModel,
                                     int maxToolRounds, int maxContextChars,
                                     long dailyTokenLimit, double dailyCostLimit,
                                     boolean auditJdbcEnabled, String auditJdbcUrl,
                                     String auditJdbcUsername, String auditJdbcPassword,
                                     int auditRetentionDays,
                                     String vectorBackend, String vectorBaseUrl,
                                     String vectorIndexName, String vectorApiKey,
                                     boolean rulesJdbcEnabled, String rulesJdbcUrl,
                                     String rulesJdbcUsername, String rulesJdbcPassword) {
    public AgentServiceProperties(String coreBaseUrl, String communityBaseUrl,
                                  Duration connectTimeout, Duration readTimeout) {
        this(coreBaseUrl, communityBaseUrl, connectTimeout, readTimeout, false, false,
                "http://127.0.0.1:11434/v1", "", "curmerce-local", 0D, 0D, "",
                false, "http://127.0.0.1:11434/v1", "", "curmerce-embedding", 2, 12000,
                0L, 0D, false, "jdbc:mysql://127.0.0.1:13306/curmerce?useSSL=false&serverTimezone=Asia/Shanghai",
                "curmerce", "", 30, "redis", "http://127.0.0.1:19200", "curmerce-agent-knowledge", "",
                false, "jdbc:mysql://127.0.0.1:13306/curmerce?useSSL=false&serverTimezone=Asia/Shanghai",
                "curmerce", "");
    }

    @ConstructorBinding
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
        embeddingBaseUrl = embeddingBaseUrl == null || embeddingBaseUrl.isBlank()
                ? "http://127.0.0.1:11434/v1" : embeddingBaseUrl.replaceAll("/$", "");
        embeddingApiKey = embeddingApiKey == null ? "" : embeddingApiKey;
        embeddingModel = embeddingModel == null || embeddingModel.isBlank() ? "curmerce-embedding" : embeddingModel;
        maxToolRounds = Math.max(0, Math.min(maxToolRounds, 5));
        maxContextChars = Math.max(1000, Math.min(maxContextChars, 50_000));
        dailyTokenLimit = Math.max(0L, Math.min(dailyTokenLimit, 100_000_000L));
        dailyCostLimit = Math.max(0D, Math.min(dailyCostLimit, 1_000_000D));
        auditJdbcUrl = auditJdbcUrl == null ? "" : auditJdbcUrl.trim();
        auditJdbcUsername = auditJdbcUsername == null ? "" : auditJdbcUsername.trim();
        auditJdbcPassword = auditJdbcPassword == null ? "" : auditJdbcPassword;
        auditRetentionDays = Math.max(1, Math.min(auditRetentionDays, 3650));
        vectorBackend = vectorBackend == null || vectorBackend.isBlank() ? "redis" : vectorBackend.trim().toLowerCase();
        if (!vectorBackend.equals("redis") && !vectorBackend.equals("elasticsearch")) vectorBackend = "redis";
        vectorBaseUrl = vectorBaseUrl == null || vectorBaseUrl.isBlank() ? "http://127.0.0.1:19200" : vectorBaseUrl.replaceAll("/$", "");
        vectorIndexName = vectorIndexName == null || vectorIndexName.isBlank() ? "curmerce-agent-knowledge" : vectorIndexName.trim();
        vectorApiKey = vectorApiKey == null ? "" : vectorApiKey;
        rulesJdbcUrl = rulesJdbcUrl == null ? "" : rulesJdbcUrl.trim();
        rulesJdbcUsername = rulesJdbcUsername == null ? "" : rulesJdbcUsername.trim();
        rulesJdbcPassword = rulesJdbcPassword == null ? "" : rulesJdbcPassword;
    }
}
