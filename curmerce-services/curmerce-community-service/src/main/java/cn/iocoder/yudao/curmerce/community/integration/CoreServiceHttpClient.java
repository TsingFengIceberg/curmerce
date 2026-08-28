package cn.iocoder.yudao.curmerce.community.integration;

import cn.iocoder.yudao.curmerce.cloud.api.CloudHeaders;
import cn.iocoder.yudao.curmerce.cloud.api.CoreMediaReferencesReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreMemberUserRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CorePermissionCheckReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreProductSummaryRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreTokenCheckReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.community.enums.ErrorCodeConstants.CORE_SERVICE_UNAVAILABLE;

@Component
@Slf4j
public class CoreServiceHttpClient {

    private final RestClient client;
    private final CircuitBreaker circuitBreaker;

    public CoreServiceHttpClient(RestClient.Builder builder, CoreServiceProperties properties,
                                 CircuitBreakerRegistry circuitBreakerRegistry) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory)
                .defaultHeader(CloudHeaders.INTERNAL_TOKEN, properties.internalToken())
                .requestInterceptor((request, body, execution) -> {
                    String traceId = MDC.get("correlationId");
                    if (traceId != null) {
                        request.getHeaders().set(CloudHeaders.TRACE_ID, traceId);
                    }
                    return execution.execute(request, body);
                }).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("coreService");
    }

    public OAuth2AccessTokenCheckRespDTO checkToken(String token) {
        return post("/internal-api/curmerce/core/auth/check", new CoreTokenCheckReqDTO().setToken(token),
                new ParameterizedTypeReference<>() {});
    }

    public boolean checkPermission(Long userId, String type, List<String> values) {
        Boolean result = post("/internal-api/curmerce/core/permission/check",
                new CorePermissionCheckReqDTO().setUserId(userId).setType(type).setValues(values),
                new ParameterizedTypeReference<>() {});
        return Boolean.TRUE.equals(result);
    }

    public DeptDataPermissionRespDTO getDeptDataPermission(Long userId) {
        return get("/internal-api/curmerce/core/permission/dept/{userId}",
                new ParameterizedTypeReference<>() {}, userId);
    }

    public CoreMemberUserRespDTO getMember(Long id) {
        return get("/internal-api/curmerce/core/member/{id}", new ParameterizedTypeReference<>() {}, id);
    }

    public void validateMember(Long id, boolean forUpdate) {
        postWithoutBody("/internal-api/curmerce/core/member/{id}/validate?forUpdate={forUpdate}",
                new ParameterizedTypeReference<>() {}, id, forUpdate);
    }

    public CoreProductSummaryRespDTO getVisibleProduct(Long id) {
        return get("/internal-api/curmerce/core/product/{id}", new ParameterizedTypeReference<>() {}, id);
    }

    public void replaceMediaReferences(CoreMediaReferencesReqDTO request) {
        post("/internal-api/curmerce/core/media/references", request, new ParameterizedTypeReference<>() {});
    }

    private <T> T get(String path, ParameterizedTypeReference<CommonResult<T>> type, Object... variables) {
        try {
            CommonResult<T> result = execute(path,
                    () -> client.get().uri(path, variables).retrieve().body(type));
            return checked(result);
        } catch (ServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw unavailable(path, ex);
        }
    }

    private <T> T post(String path, Object body, ParameterizedTypeReference<CommonResult<T>> type) {
        try {
            CommonResult<T> result = execute(path,
                    () -> client.post().uri(path).body(body).retrieve().body(type));
            return checked(result);
        } catch (ServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw unavailable(path, ex);
        }
    }

    private <T> T postWithoutBody(String path, ParameterizedTypeReference<CommonResult<T>> type,
                                  Object... variables) {
        try {
            CommonResult<T> result = execute(path,
                    () -> client.post().uri(path, variables).retrieve().body(type));
            return checked(result);
        } catch (ServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw unavailable(path, ex);
        }
    }

    private static <T> T checked(CommonResult<T> result) {
        if (result == null) {
            throw new ServiceException(CORE_SERVICE_UNAVAILABLE);
        }
        return result.getCheckedData();
    }

    private <T> T execute(String path, Supplier<T> request) {
        try {
            return circuitBreaker.executeSupplier(request);
        } catch (RuntimeException ex) {
            throw unavailable(path, ex);
        }
    }

    private ServiceException unavailable(String path, RuntimeException cause) {
        log.warn("core service call failed: path={}, correlationId={}, breakerState={}, reason={}", path,
                MDC.get("correlationId"), circuitBreaker.getState(), cause.getMessage());
        return new ServiceException(CORE_SERVICE_UNAVAILABLE);
    }
}
