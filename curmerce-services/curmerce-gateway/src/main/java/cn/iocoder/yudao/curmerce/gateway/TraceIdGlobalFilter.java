package cn.iocoder.yudao.curmerce.gateway;

import cn.iocoder.yudao.curmerce.cloud.api.CloudHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String candidate = exchange.getRequest().getHeaders().getFirst(CloudHeaders.TRACE_ID);
        String traceId = candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()
                ? candidate : UUID.randomUUID().toString().replace("-", "");
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CloudHeaders.TRACE_ID, traceId)).build();
        exchange.getResponse().getHeaders().set(CloudHeaders.TRACE_ID, traceId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
