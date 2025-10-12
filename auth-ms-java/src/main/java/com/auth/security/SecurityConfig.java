package com.auth.security;

import com.nimbusds.jose.JOSEException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.util.stream.Stream;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

  private final JwkProvider jwkProvider;
  @Autowired
  public SecurityConfig(JwkProvider jwkProvider){
    this.jwkProvider = jwkProvider;
  }

  // Cadena pública: no aplica Resource Server
  @Bean
  @Order(1)
  SecurityWebFilterChain publicChain(ServerHttpSecurity http) {
    http
        .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/auth/**", "/.well-known/**", "/actuator/health"))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .authorizeExchange(auth -> auth
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyExchange().permitAll()
        );
    return http.build();
  }

  // Cadena protegida: aplica Resource Server
  @Bean
  @Order(2)
  SecurityWebFilterChain appChain(ServerHttpSecurity http) {
    http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .authorizeExchange(auth -> auth
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .pathMatchers("/me/**").authenticated()
            .anyExchange().authenticated()
        )
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())));
    return http.build();
  }

  @Bean
  public NimbusReactiveJwtDecoder jwtDecoder() throws JOSEException {
    RSAPublicKey publicKey = (RSAPublicKey) jwkProvider.getRsaKey().toPublicKey();
    return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
  }

  private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthConverter() {
    JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
    gac.setAuthorityPrefix("ROLE_");
    gac.setAuthoritiesClaimName("roles");

    var delegate = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
    delegate.setJwtGrantedAuthoritiesConverter(jwt -> {
      var roleAuths = gac.convert(jwt);
      // Agregar scopes como autoridades SCOPE_*
      var scopeClaim = jwt.getClaimAsStringList("scopes");
      if (scopeClaim != null) {
        var scopeAuths = scopeClaim.stream().map(s -> "SCOPE_" + s).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();
        return Stream.concat(roleAuths.stream(), scopeAuths.stream()).toList();
      }
      return roleAuths;
    });
    return new ReactiveJwtAuthenticationConverterAdapter(delegate);
  }
}
