package cn.iocoder.yudao.curmerce.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/** Creates the optional durable platform-rule store without coupling it to the core datasource. */
@Configuration
@ConditionalOnProperty(prefix = "curmerce.agent", name = "rules-jdbc-enabled", havingValue = "true")
public class AgentRuleJdbcConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AgentRuleJdbcConfiguration.class);

    @Bean
    DataSource agentRuleDataSource(AgentServiceProperties properties) {
        AgentAuditJdbcConfiguration.validate("rule", properties.rulesJdbcUrl(), properties.rulesJdbcUsername());
        log.info("Agent rule store enabled for JDBC endpoint {}", AgentAuditJdbcConfiguration.endpoint(properties.rulesJdbcUrl()));
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(properties.rulesJdbcUrl());
        dataSource.setUsername(properties.rulesJdbcUsername());
        dataSource.setPassword(properties.rulesJdbcPassword());
        return dataSource;
    }

    @Bean
    AgentRuleJdbcStore agentRuleJdbcStore(@Qualifier("agentRuleDataSource") DataSource agentRuleDataSource) {
        AgentRuleJdbcStore store = new AgentRuleJdbcStore(agentRuleDataSource);
        store.ensureSchema();
        return store;
    }
}
