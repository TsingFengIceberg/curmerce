package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentServicePropertiesBindingTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "curmerce.agent.core-base-url=http://core.example",
                    "curmerce.agent.audit-jdbc-enabled=true",
                    "curmerce.agent.audit-jdbc-url=jdbc:mysql://127.0.0.1:13306/curmerce?useSSL=false&serverTimezone=Asia/Shanghai",
                    "curmerce.agent.rules-jdbc-enabled=true",
                    "curmerce.agent.max-tool-rounds=3");

    @Test
    void bindsRecordWithAuxiliaryConstructorForDurableAgentModes() {
        context.run(application -> {
            AgentServiceProperties properties = application.getBean(AgentServiceProperties.class);
            assertEquals("http://core.example", properties.coreBaseUrl());
            assertEquals(true, properties.auditJdbcEnabled());
            assertEquals("jdbc:mysql://127.0.0.1:13306/curmerce?useSSL=false&serverTimezone=Asia/Shanghai",
                    properties.auditJdbcUrl());
            assertEquals(true, properties.rulesJdbcEnabled());
            assertEquals(3, properties.maxToolRounds());
        });
    }

    @Test
    void createsProviderHealthServiceWhenTestConstructorIsAlsoPresent() {
        new ApplicationContextRunner().withUserConfiguration(ProviderHealthConfiguration.class)
                .withPropertyValues("curmerce.agent.spring-ai-enabled=false")
                .run(application -> org.junit.jupiter.api.Assertions.assertNotNull(
                        application.getBean(AgentProviderHealthService.class)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentServiceProperties.class)
    static class PropertiesConfiguration { }

    @Configuration(proxyBeanMethods = false)
    @Import(AgentProviderHealthService.class)
    @EnableConfigurationProperties(AgentServiceProperties.class)
    static class ProviderHealthConfiguration {
        @Bean
        ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
