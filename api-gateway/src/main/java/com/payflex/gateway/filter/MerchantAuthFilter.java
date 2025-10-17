package com.payflex.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class MerchantAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MerchantAuthFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (path != null && (path.equals("/merchant") || path.startsWith("/merchant/"))) {
            List<String> auth = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
            if (auth == null || auth.isEmpty() || auth.get(0).isBlank()) {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                log.warn("[GATEWAY][AUTH] Blocking request to {} - missing Authorization header", path);
                return response.setComplete();
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run after the very first filters but before most others
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

