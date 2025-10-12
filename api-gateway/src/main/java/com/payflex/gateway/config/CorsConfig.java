package com.payflex.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class CorsConfig {

  @Bean
  public CorsWebFilter corsWebFilter(Environment env) {
    CorsConfiguration corsConfig = new CorsConfiguration();

    String defaultOrigins = String.join(",",
        "http://localhost:3000",
        "http://localhost:3001",
        "http://localhost:5173",
        "http://localhost:5174"
    );

    String originsCsv = env.getProperty("ALLOWED_ORIGINS", defaultOrigins);
    String patternsCsv = env.getProperty("ALLOWED_ORIGIN_PATTERNS", "");

    List<String> origins = Arrays.stream(originsCsv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());

    if (!patternsCsv.isBlank()) {
      List<String> patterns = Arrays.stream(patternsCsv.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .collect(Collectors.toList());
      corsConfig.setAllowedOriginPatterns(patterns);
    } else {
      corsConfig.setAllowedOrigins(origins);
    }

    corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    corsConfig.setAllowedHeaders(List.of("*"));
    corsConfig.setAllowCredentials(true);
    corsConfig.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", corsConfig);

    return new CorsWebFilter(source);
  }
}
