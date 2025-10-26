package com.payflex.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(DatabaseUrlEnvironmentPostProcessor.class);
  private static final String SOURCE_NAME = "env-railway-db";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    try {
      boolean hasR2dbc = environment.containsProperty("spring.r2dbc.url");
      boolean hasDatasource = environment.containsProperty("spring.datasource.url");

      if (hasR2dbc && hasDatasource) {
        log.info("[DBCFG] Datasource y R2DBC ya configurados, no se sobreescribe.");
        return;
      }

      // === 1️⃣ Leer variables desde entorno (Railway o local) ===
      String username = System.getenv().getOrDefault("DB_USER", "postgres");
      String password = System.getenv().getOrDefault("DB_PASSWORD", "postgres");
      String host = System.getenv().getOrDefault("DB_HOST", "localhost");
      String port = System.getenv().getOrDefault("DB_PORT", "5432");
      String db = System.getenv().getOrDefault("DB_NAME", "postgres");

      // === 2️⃣ Construir URLs dinámicamente ===
      String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + db;
      String r2dbcUrl = "r2dbc:postgresql://" + username + ":" + password + "@" + host + ":" + port + "/" + db;

      Map<String, Object> props = new HashMap<>();

      // === 3️⃣ Asignar JDBC (para JPA/Hibernate) ===
      if (!hasDatasource) {
        props.put("spring.datasource.url", jdbcUrl);
        props.put("spring.datasource.username", username);
        props.put("spring.datasource.password", password);
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
      }

      // === 4️⃣ Asignar R2DBC (para acceso reactivo) ===
      if (!hasR2dbc) {
        props.put("spring.r2dbc.url", r2dbcUrl);
        props.put("spring.r2dbc.username", username);
        props.put("spring.r2dbc.password", password);
      }

      // === 5️⃣ Inyectar propiedades ===
      environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));

      log.info("[DBCFG] Propiedades de base de datos inyectadas desde entorno ({})", props.keySet());
      log.info("[DBCFG] Conectando a PostgreSQL -> {}:{}", host, port);

    } catch (Exception e) {
      log.warn("[DBCFG] Error configurando DB desde entorno: {}", e.getMessage());
    }
  }
}
