package com.payflex.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

  private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

  @Value("${spring.data.redis.host:localhost}")
  private String redisHost;

  @Value("${spring.data.redis.port:6379}")
  private int redisPort;

  @Value("${spring.data.redis.password:}")
  private String redisPassword;

  @Value("${spring.data.redis.username:default}")
  private String redisUsername;

  @Bean
  @Primary
  public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
    log.info("Configurando Redis: host={}, port={}, username={}, passwordConfigured={}",
             redisHost, redisPort, redisUsername, (redisPassword != null && !redisPassword.isEmpty()));

    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
    config.setHostName(redisHost);
    config.setPort(redisPort);
    config.setUsername(redisUsername);

    if (redisPassword != null && !redisPassword.isEmpty()) {
      config.setPassword(RedisPassword.of(redisPassword));
      log.debug("Password de Redis configurado");
    } else {
      log.warn("Redis sin password configurado - esto puede causar problemas si el servidor requiere autenticación");
    }

    LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
    factory.afterPropertiesSet();
    log.info("ReactiveRedisConnectionFactory creado exitosamente");
    return factory;
  }

  @Bean
  public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
      ReactiveRedisConnectionFactory connectionFactory) {

    StringRedisSerializer serializer = new StringRedisSerializer();
    RedisSerializationContext<String, String> context = RedisSerializationContext
        .<String, String>newSerializationContext(serializer)
        .key(serializer)
        .value(serializer)
        .hashKey(serializer)
        .hashValue(serializer)
        .build();

    return new ReactiveRedisTemplate<>(connectionFactory, context);
  }
}

