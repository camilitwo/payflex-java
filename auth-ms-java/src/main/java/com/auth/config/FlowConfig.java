package com.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class FlowConfig {

    @Value("${flow.base-url}")
    private String flowBaseUrl;

    @Bean
    public WebClient flowWebClient() {
        return WebClient.builder()
                .baseUrl(flowBaseUrl)
                .build();
    }
}
