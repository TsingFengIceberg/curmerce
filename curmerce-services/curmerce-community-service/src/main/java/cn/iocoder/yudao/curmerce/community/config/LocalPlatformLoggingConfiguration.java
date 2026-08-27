package cn.iocoder.yudao.curmerce.community.config;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.logger.OperateLogCommonApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LocalPlatformLoggingConfiguration {

    @Bean
    ApiErrorLogCommonApi apiErrorLogCommonApi() {
        return request -> { };
    }

    @Bean
    ApiAccessLogCommonApi apiAccessLogCommonApi() {
        return request -> { };
    }

    @Bean
    OperateLogCommonApi operateLogCommonApi() {
        return request -> { };
    }
}
