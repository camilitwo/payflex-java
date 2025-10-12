package com.payflex.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
    http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(auth -> auth
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .pathMatchers("/actuator/health").permitAll()
            .pathMatchers("/auth/**").permitAll()
            .pathMatchers("/api/**").authenticated()
            .anyExchange().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor()))
        );
    return http.build();
  }

  @Bean
  public ReactiveJwtDecoder jwtDecoder(Environment env) {
    String base = resolveAuthBase(env);
    String jwkSetUri = base.endsWith("/") ? base + ".well-known/jwks.json" : base + "/.well-known/jwks.json";
    log.info("Security: AUTH_BFF JWKS URI='{}'", jwkSetUri);
    return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
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
