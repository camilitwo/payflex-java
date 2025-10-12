package com.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    private final Environment env;

    public StartupLogger(Environment env) {
        this.env = env;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("==================================================================");
        log.info("🚀 AUTH-BFF SERVICE STARTED SUCCESSFULLY");
        log.info("==================================================================");
        log.info("Port: {}", env.getProperty("server.port", "8081"));
        log.info("Profile: {}", String.join(", ", env.getActiveProfiles()));
        log.info("------------------------------------------------------------------");
        log.info("PocketBase Configuration:");
        log.info("  - URL: {}", env.getProperty("pocketbase.url", "not set"));
        log.info("  - Collection: {}", env.getProperty("pocketbase.collection", "not set"));
        log.info("  - Merchant Field: {}", env.getProperty("pocketbase.merchant-field", "merchantId"));
        log.info("  - Roles Field: {}", env.getProperty("pocketbase.roles-field", "roles"));
        log.info("------------------------------------------------------------------");
        log.info("Merchant Service Configuration:");
        log.info("  - URL: {}", env.getProperty("merchant.service.url", "not set"));
        log.info("  - Connect Timeout: {}ms", env.getProperty("merchant.service.timeout.connect", "5000"));
        log.info("  - Read Timeout: {}ms", env.getProperty("merchant.service.timeout.read", "10000"));
        log.info("------------------------------------------------------------------");
        log.info("Auth Configuration:");
        log.info("  - Issuer: {}", env.getProperty("auth.issuer", "not set"));
        log.info("  - Audience: {}", env.getProperty("auth.audience", "not set"));
        log.info("  - Access Token TTL: {} minutes", env.getProperty("auth.access-token-ttl-minutes", "15"));
        log.info("  - Refresh Token TTL: {} days", env.getProperty("auth.refresh-token-ttl-days", "7"));
        log.info("==================================================================");
    }
}

