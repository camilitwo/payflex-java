package com.payflex.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = Instant.now().toEpochMilli();

        String requestId = request.getId();
        String method = request.getMethod().toString();
        String path = request.getURI().getPath();
        String remoteAddress = request.getRemoteAddress() != null ?
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        log.info("🟢 [GATEWAY][REQUEST] {} {} from {} | ID: {}",
            method, path, remoteAddress, requestId);

        // Log headers importantes
        request.getHeaders().forEach((name, values) -> {
            if (name.toLowerCase().contains("authorization") ||
                name.toLowerCase().contains("content-type") ||
                name.toLowerCase().contains("host")) {
                log.debug("🟢 [GATEWAY][HEADER] {}: {}", name,
                    name.toLowerCase().contains("authorization") ? "[REDACTED]" : values);
            }
        });

        return chain.filter(exchange)
            .doOnSuccess(v -> {
                ServerHttpResponse response = exchange.getResponse();
                long duration = Instant.now().toEpochMilli() - startTime;
                int statusCode = response.getStatusCode() != null ?
                    response.getStatusCode().value() : 0;

                if (statusCode >= 200 && statusCode < 400) {
                    log.info("✅ [GATEWAY][RESPONSE] {} {} → {} | Duration: {}ms | ID: {}",
                        method, path, statusCode, duration, requestId);
                } else if (statusCode >= 400 && statusCode < 500) {
                    log.warn("⚠️  [GATEWAY][RESPONSE] {} {} → {} | Duration: {}ms | ID: {}",
                        method, path, statusCode, duration, requestId);
                } else if (statusCode >= 500) {
                    log.error("🔴 [GATEWAY][RESPONSE] {} {} → {} | Duration: {}ms | ID: {}",
                        method, path, statusCode, duration, requestId);
                } else {
                    log.info("🟢 [GATEWAY][RESPONSE] {} {} → {} | Duration: {}ms | ID: {}",
                        method, path, statusCode, duration, requestId);
                }
            })
            .doOnError(err -> {
                long duration = Instant.now().toEpochMilli() - startTime;
                log.error("🔴 [GATEWAY][ERROR] {} {} failed after {}ms | ID: {} | Error: {}",
                    method, path, duration, requestId, err.getMessage(), err);
            });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

