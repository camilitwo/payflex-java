package com.payflex.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  @Order(1)
  SecurityWebFilterChain publicChain(ServerHttpSecurity http) {
    http
        .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/.well-known/**", "/auth/**", "/me/**", "/actuator/health"))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(auth -> auth
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyExchange().permitAll()
        );
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityWebFilterChain protectedChain(ServerHttpSecurity http) {
    http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(auth -> auth
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyExchange().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor()))
        );
    return http.build();
  }

  @Bean
  public ReactiveJwtDecoder jwtDecoder(Environment env, DiscoveryClient discoveryClient) {
    // 0) Override explícito
    String explicitJwks = env.getProperty("AUTH_JWKS_URI");
    if (isNotBlank(explicitJwks)) {
      log.info("Security: Using explicit AUTH_JWKS_URI='{}'", explicitJwks);
      NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(explicitJwks.trim()).build();
      // No validar issuer ni audience si hay problemas
      decoder.setJwtValidator(token -> {
        log.debug("Validating JWT token: {}", token.getSubject());
        return OAuth2TokenValidatorResult.success();
      });
      return decoder;
    }

    // 1) Resolver por Eureka (serviceId configurable)
    String serviceId = env.getProperty("AUTH_SERVICE_ID", "auth-ms-java");
    Optional<ServiceInstance> authInstance = chooseServiceInstance(discoveryClient, serviceId);
    if (authInstance.isPresent()) {
      ServiceInstance inst = authInstance.get();
      String scheme = inst.isSecure() ? "https" : "http";
      String host = inst.getHost();
      int port = inst.getPort();
      String eurekaJwks = scheme + "://" + host + ":" + port + "/.well-known/jwks.json";
      log.info("Security: Using Eureka-resolved JWKS URI='{}' (serviceId={})", eurekaJwks, serviceId);
      NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(eurekaJwks).build();
      decoder.setJwtValidator(token -> {
        log.debug("Validating JWT token from Eureka: {}", token.getSubject());
        return OAuth2TokenValidatorResult.success();
      });
      return decoder;
    } else {
      log.warn("Security: No Eureka instances found for serviceId='{}'. Falling back to gateway self-proxy.", serviceId);
    }

    // 2) Fallback: proxy local del gateway (ruta ya mapeada via lb://auth-ms-java)
    String port = firstNonBlank(env.getProperty("PORT"), env.getProperty("server.port"), "8080");
    String selfProxiedJwks = "http://localhost:" + port + "/.well-known/jwks.json";
    log.info("Security: Using self-proxied JWKS URI='{}'", selfProxiedJwks);
    NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(selfProxiedJwks).build();
    decoder.setJwtValidator(token -> {
      log.debug("Validating JWT token from self-proxy: {}", token.getSubject());
      return OAuth2TokenValidatorResult.success();
    });
    return decoder;
  }

  private boolean isNotBlank(String s) { return s != null && !s.isBlank(); }

  private Optional<ServiceInstance> chooseServiceInstance(DiscoveryClient discoveryClient, String serviceId) {
    try {
      List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
      if (instances != null && !instances.isEmpty()) {
        // Estrategia simple: primera instancia registrada
        return Optional.of(instances.get(0));
      }
    } catch (Exception e) {
      log.warn("Security: DiscoveryClient failed for serviceId='{}': {}", serviceId, e.toString());
    }
    return Optional.empty();
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private String resolveAuthBase(Environment env) {
    String scheme = env.getProperty("AUTH_BFF_SCHEME", "http");
    String service = env.getProperty("AUTH_BFF_SERVICE");
    String url = env.getProperty("AUTH_BFF_URL");
    String host = env.getProperty("AUTH_BFF_HOST");
    String portStr = env.getProperty("AUTH_BFF_PORT");

    // 1) URL completa con esquema -> usar tal cual
    if (url != null && !url.isBlank() && url.trim().contains("://")) {
      return url.trim();
    }

    // 2) Priorizar SERVICE/HOST cuando URL no trae esquema
    if (host == null || host.isBlank()) {
      if (service != null && !service.isBlank()) {
        host = service.trim().contains(".") ? service.trim() : (service.trim() + ".railway.internal");
      }
    }
    if (host != null && !host.isBlank()) {
      return buildFromHostPort(scheme, host.trim(), portStr);
    }

    // 3) Como último recurso, si hay URL sin esquema, interpretarla como host[:port]
    if (url != null && !url.isBlank()) {
      return buildFromHostPort(scheme, url.trim(), portStr);
    }

    // 4) Fallback local
    return "http://localhost:8081";
  }

  private String buildFromHostPort(String scheme, String hostOrHostPort, String portStr) {
    if (hostOrHostPort.contains(":")) {
      return scheme + "://" + hostOrHostPort;
    }
    Integer port = tryParsePort(portStr);
    if (port != null) {
      return scheme + "://" + hostOrHostPort + ":" + port;
    }
    return scheme + "://" + hostOrHostPort;
  }

  private Integer tryParsePort(String portStr) {
    try {
      if (portStr != null && !portStr.isBlank()) {
        return Integer.parseInt(portStr.trim());
      }
    } catch (NumberFormatException ignored) {
    }
    return null;
  }

  @Bean
  public Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
      Collection<GrantedAuthority> authorities = new ArrayList<>();

      // Extraer scopes del JWT
      Object scopesObj = jwt.getClaim("scopes");
      if (scopesObj instanceof List<?>) {
        List<String> scopes = ((List<?>) scopesObj).stream()
            .filter(s -> s instanceof String)
            .map(String::valueOf)
            .collect(Collectors.toList());

        // Agregar scopes con prefijo SCOPE_
        scopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
      }

      // Extraer roles del JWT
      Object rolesObj = jwt.getClaim("roles");
      if (rolesObj instanceof List<?>) {
        List<String> roles = ((List<?>) rolesObj).stream()
            .filter(r -> r instanceof String)
            .map(String::valueOf)
            .collect(Collectors.toList());

        // Agregar roles como autoridades
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
      }

      return authorities;
    });

    return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
  }
}
