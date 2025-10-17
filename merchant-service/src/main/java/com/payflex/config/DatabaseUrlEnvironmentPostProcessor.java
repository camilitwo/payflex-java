package com.payflex.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(DatabaseUrlEnvironmentPostProcessor.class);
  private static final String SOURCE_NAME = "converted-database-url";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    try {
      // Check if already configured
      boolean hasR2dbc = environment.containsProperty("spring.r2dbc.url") || environment.containsProperty("SPRING_R2DBC_URL");
      boolean hasDatasource = environment.containsProperty("spring.datasource.url") || environment.containsProperty("SPRING_DATASOURCE_URL");

      if (hasR2dbc && hasDatasource) {
        log.debug("DatabaseUrlEnvPostProcessor: spring.r2dbc.url and spring.datasource.url already present, skipping conversion.");
        return;
      }

      String databaseUrl = environment.getProperty("DATABASE_URL");
      if (databaseUrl == null || databaseUrl.isBlank()) {
        log.debug("DatabaseUrlEnvPostProcessor: DATABASE_URL not present, nothing to convert.");
        return;
      }

      log.info("DatabaseUrlEnvPostProcessor: detected DATABASE_URL, converting to Spring properties...");

      // Parse URL like: postgres://user:pass@host:port/dbname
      URI uri = new URI(databaseUrl);

      String userInfo = uri.getUserInfo(); // user:pass
      String username = null;
      String password = null;
      if (userInfo != null && !userInfo.isBlank()) {
        String[] parts = userInfo.split(":", 2);
        username = parts.length > 0 ? parts[0] : null;
        password = parts.length > 1 ? parts[1] : null;
      }

      String host = uri.getHost();
      int port = uri.getPort() == -1 ? 5432 : uri.getPort();
      String path = uri.getPath(); // /dbname
      String database = (path != null && path.length() > 1) ? path.substring(1) : "";

      // Build R2DBC and JDBC URLs
      String r2dbcUrl;
      if (databaseUrl.startsWith("r2dbc:")) {
        r2dbcUrl = databaseUrl;
      } else {
        // convert postgres://user:pass@host:port/db -> r2dbc:postgresql://user:pass@host:port/db
        r2dbcUrl = databaseUrl.replaceFirst("^postgres://", "r2dbc:postgresql://");
      }

      String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
      // Preserve query parameters (e.g. sslmode=require) if present
      String query = uri.getQuery();
      if (query != null && !query.isBlank()) {
        jdbcUrl = jdbcUrl + "?" + query;
      }

      Map<String, Object> props = new HashMap<>();
      if (!hasR2dbc) props.put("spring.r2dbc.url", r2dbcUrl);
      if (!hasDatasource) props.put("spring.datasource.url", jdbcUrl);
      if (username != null && !environment.containsProperty("spring.datasource.username") && !environment.containsProperty("SPRING_DATASOURCE_USERNAME")) {
        props.put("spring.datasource.username", username);
      }
      if (password != null && !environment.containsProperty("spring.datasource.password") && !environment.containsProperty("SPRING_DATASOURCE_PASSWORD")) {
        props.put("spring.datasource.password", password);
      }
      // Also set R2DBC username/password properties if driver doesn't parse them from URL
      if (username != null && !environment.containsProperty("spring.r2dbc.username") && !environment.containsProperty("SPRING_R2DBC_USERNAME")) {
        props.put("spring.r2dbc.username", username);
      }
      if (password != null && !environment.containsProperty("spring.r2dbc.password") && !environment.containsProperty("SPRING_R2DBC_PASSWORD")) {
        props.put("spring.r2dbc.password", password);
      }

      if (!props.isEmpty()) {
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
        log.info("DatabaseUrlEnvPostProcessor: injected properties: {}", props.keySet());
      } else {
        log.debug("DatabaseUrlEnvPostProcessor: no properties to inject.");
      }

    } catch (Exception e) {
      log.warn("DatabaseUrlEnvPostProcessor: failed to convert DATABASE_URL: {}", e.getMessage());
    }
  }
}
