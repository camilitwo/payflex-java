package com.payflex.merchant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment env;

    public StartupLogger(Environment env) {
        this.env = env;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("==================================================================");
        log.info("🚀 MERCHANT SERVICE STARTED SUCCESSFULLY");
        log.info("==================================================================");
        log.info("Port: {}", env.getProperty("server.port", "8083"));
        log.info("Profile: {}", String.join(", ", env.getActiveProfiles()));
        log.info("------------------------------------------------------------------");
        log.info("Database Configuration:");
        log.info("  - R2DBC URL: {}", maskPassword(env.getProperty("spring.r2dbc.url", "not set")));
        log.info("  - Username: {}", env.getProperty("spring.r2dbc.username", "not set"));
        log.info("  - Pool Initial Size: {}", env.getProperty("spring.r2dbc.pool.initial-size", "10"));
        log.info("  - Pool Max Size: {}", env.getProperty("spring.r2dbc.pool.max-size", "20"));
        log.info("------------------------------------------------------------------");
        log.info("CORS Configuration:");
        log.info("  - Allowed Origins: {}", env.getProperty("cors.allowed-origins", "not set"));
        log.info("==================================================================");
    }

    private String maskPassword(String url) {
        if (url == null || !url.contains("password=")) {
            return url;
        }
        return url.replaceAll("password=([^&]+)", "password=****");
    }
}

