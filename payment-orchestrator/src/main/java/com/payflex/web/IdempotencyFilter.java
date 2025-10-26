package com.payflex.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;

import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@Order(-10)
@ConditionalOnProperty(name = "idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyFilter implements org.springframework.web.server.WebFilter {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
  private final ReactiveRedisTemplate<String, String> redis;

  @Value("${idempotency.fail-on-redis-error:false}")
  private boolean failOnRedisError;

  public IdempotencyFilter(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
    this.redis = redis;
    log.info("IdempotencyFilter activado - failOnRedisError={}", failOnRedisError);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
      return chain.filter(exchange);
    }

    String key = exchange.getRequest().getHeaders().getFirst("Idempotency-Key");
    if (key == null || key.isBlank()) {
      log.warn("POST request sin Idempotency-Key header");
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Idempotency-Key"));
    }

    String redisKey = "idem:" + key;

    return redis.opsForValue()
        .setIfAbsent(redisKey, "1", Duration.ofHours(24))
        .timeout(Duration.ofSeconds(2))
        .flatMap(wasSet -> {
          if (Boolean.FALSE.equals(wasSet)) {
            log.warn("Request duplicado detectado: {}", key);
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicated request"));
          }
          log.debug("Idempotency key registrada: {}", key);
          return chain.filter(exchange);
        })
        .onErrorResume(ex -> {
          log.error("Error en verificación de idempotencia (Redis no disponible): {} - Permitiendo request", ex.getMessage());
          // Si Redis falla, permitimos que el request continúe (modo degradado)
          return chain.filter(exchange);
        });
  }
}
