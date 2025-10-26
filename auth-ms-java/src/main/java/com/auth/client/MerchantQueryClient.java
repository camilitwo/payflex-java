package com.auth.client;


import com.auth.dto.DashboardStatsDto;
import com.auth.dto.MeMerchantConfigDto;
import com.auth.dto.MeMerchantDto;
import com.auth.dto.MeMerchantUserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class MerchantQueryClient {

    private final WebClient client;

    public MerchantQueryClient(org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder,
                               @Value("${merchant.service.url:http://merchant-service}") String baseUrl) {
        this.client = webClientBuilder
            .baseUrl(baseUrl)
            .filter(logRequest())
            .filter(logResponse())
            .build();
    }

    private ExchangeFilterFunction logRequest() {
        return (req, next) -> {
            log.debug("[MQC][REQ] {} {}", req.method(), req.url());
            return next.exchange(req);
        };
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(resp -> {
            log.debug("[MQC][RES] status={}", resp.statusCode());
            return Mono.just(resp);
        });
    }

    public Mono<MeMerchantDto> getMerchant(String merchantId, String bearer) {
        return client.get()
            .uri("/merchants/{id}", merchantId)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToMono(MeMerchantDto.class)
            .timeout(Duration.ofSeconds(5))
            .doOnError(e -> log.error("[MQC][ERR] getMerchant id={} msg={}", merchantId, e.getMessage()));
    }

    public Flux<MeMerchantUserDto> getMerchantUsers(String merchantId, String bearer) {
        return client.get()
            .uri("/merchants/{id}/users", merchantId)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToFlux(MeMerchantUserDto.class)
            .timeout(Duration.ofSeconds(5))
            .doOnError(e -> log.error("[MQC][ERR] getMerchantUsers id={} msg={}", merchantId, e.getMessage()))
            .onErrorResume(e -> Flux.empty());
    }

    public Mono<MeMerchantConfigDto> getMerchantConfig(String merchantId, String bearer) {
        return client.get()
            .uri("/merchants/{id}/config", merchantId)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToMono(MeMerchantConfigDto.class)
            .timeout(Duration.ofSeconds(5))
            .doOnError(e -> log.error("[MQC][ERR] getMerchantConfig id={} msg={}", merchantId, e.getMessage()))
            .onErrorResume(e -> Mono.empty());
    }

    public Mono<DashboardStatsDto> getDashboardStats(String merchantId, String bearer) {
        return client.get()
            .uri("/merchants/{id}/dashboard/stats", merchantId)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToMono(DashboardStatsDto.class)
            .timeout(Duration.ofSeconds(5))
            .doOnError(e -> log.error("[MQC][ERR] getDashboardStats id={} msg={}", merchantId, e.getMessage()))
            .onErrorResume(e -> Mono.empty());
    }
}
