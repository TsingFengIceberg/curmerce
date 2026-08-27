package cn.iocoder.yudao.curmerce.community.integration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static cn.iocoder.yudao.module.community.enums.ErrorCodeConstants.CORE_SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreServiceHttpClientTest {

    @Test
    void unavailableCoreMapsToStableDomainError() {
        CoreServiceProperties properties = new CoreServiceProperties("http://127.0.0.1:1",
                "0123456789abcdef0123456789abcdef", Duration.ofMillis(100), Duration.ofMillis(100));
        CoreServiceHttpClient client = new CoreServiceHttpClient(RestClient.builder(), properties);

        ServiceException error = assertThrows(ServiceException.class, () -> client.getMember(1L));

        assertEquals(CORE_SERVICE_UNAVAILABLE.getCode(), error.getCode());
    }
}
