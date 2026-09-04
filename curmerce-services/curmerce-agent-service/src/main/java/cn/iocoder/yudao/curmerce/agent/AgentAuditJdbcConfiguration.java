package cn.iocoder.yudao.curmerce.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/** Optional JDBC archive for audit records. It is deliberately disabled by default. */
@Configuration
@ConditionalOnProperty(prefix = "curmerce.agent", name = "audit-jdbc-enabled", havingValue = "true")
public class AgentAuditJdbcConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AgentAuditJdbcConfiguration.class);

    @Bean
    DataSource agentAuditDataSource(AgentServiceProperties properties) {
        validate("audit", properties.auditJdbcUrl(), properties.auditJdbcUsername());
        log.info("Agent audit archive enabled for JDBC endpoint {}", endpoint(properties.auditJdbcUrl()));
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(properties.auditJdbcUrl());
        dataSource.setUsername(properties.auditJdbcUsername());
        dataSource.setPassword(properties.auditJdbcPassword());
        return dataSource;
    }

    static void validate(String name, String url, String username) {
        if (url == null || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("Agent " + name + " JDBC URL must use jdbc:mysql://");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Agent " + name + " JDBC username must not be blank");
        }
    }

    static String endpoint(String url) {
        if (url == null) return "<unset>";
        int query = url.indexOf('?');
        return query >= 0 ? url.substring(0, query) : url;
    }

    @Bean
    AgentAuditJdbcArchive agentAuditJdbcArchive(@Qualifier("agentAuditDataSource") DataSource dataSource, AgentServiceProperties properties) {
        return new AgentAuditJdbcArchive(dataSource, properties.auditRetentionDays());
    }

    @Bean
    AgentUsageJdbcArchive agentUsageJdbcArchive(@Qualifier("agentAuditDataSource") DataSource dataSource,
                                                AgentServiceProperties properties) {
        AgentUsageJdbcArchive archive = new AgentUsageJdbcArchive(dataSource, properties.auditRetentionDays());
        archive.ensureSchema();
        return archive;
    }
}
