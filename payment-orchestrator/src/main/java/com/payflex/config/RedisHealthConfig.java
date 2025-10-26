package com.payflex.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
public class RedisHealthConfig {

  private static final Logger log = LoggerFactory.getLogger(RedisHealthConfig.class);

  @Bean
  @Primary
  public ReactiveHealthIndicator redisHealthIndicator(
      @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
    return () -> redisTemplate.execute(connection -> connection.ping())
        .next()
        .timeout(Duration.ofSeconds(2))
        .map(response -> {
          log.debug("Redis health check exitoso: {}", response);
          return Health.up()
              .withDetail("status", "connected")
              .withDetail("response", response)
              .build();
        })
        .onErrorResume(ex -> {
          log.warn("Redis health check falló (no crítico): {}", ex.getMessage());
          return Mono.just(Health.down()
              .withDetail("status", "disconnected")
              .withDetail("error", ex.getMessage())
              .withDetail("note", "La aplicación puede seguir funcionando sin Redis")
              .build());
        })
        .defaultIfEmpty(Health.unknown().build());
  }
}

