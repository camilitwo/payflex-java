package com.auth.client;


import com.auth.dto.CreatePaymentIntentRequest;
import com.auth.dto.CreateWithdrawalRequest;
import com.auth.dto.DashboardStatsDto;
import com.auth.dto.MeMerchantConfigDto;
import com.auth.dto.MeMerchantDto;
import com.auth.dto.MeMerchantUserDto;
import com.auth.dto.MerchantBalanceDto;
import com.auth.dto.PaymentIntentDto;
import com.auth.dto.TransactionListDto;
import com.auth.dto.WithdrawalDto;
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

    public Mono<TransactionListDto> getTransactions(String merchantId, String bearer, String status, int page, int pageSize) {
        return client.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/payment-intents/merchant/{merchantId}/transactions")
                .queryParam("status", status)
                .queryParam("page", page)
                .queryParam("pageSize", pageSize)
                .build(merchantId))
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToMono(TransactionListDto.class)
            .timeout(Duration.ofSeconds(10))
            .doOnError(e -> log.error("[MQC][ERR] getTransactions merchantId={} status={} page={} msg={}",
                merchantId, status, page, e.getMessage()))
            .onErrorResume(e -> Mono.empty());
    }

    public Mono<PaymentIntentDto> createPaymentIntent(CreatePaymentIntentRequest request, String bearer) {
        return client.post()
            .uri("/api/payment-intents")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PaymentIntentDto.class)
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess(pi -> log.info("[MQC][SUCCESS] createPaymentIntent id={}", pi != null ? pi.getId() : "null"))
            .doOnError(e -> log.error("[MQC][ERR] createPaymentIntent merchantId={} amount={} msg={}",
                request.getMerchantId(), request.getAmount(), e.getMessage()));
    }

    // ==================== WITHDRAWALS ====================

    /**
     * Obtener el balance disponible del merchant
     */
    public Mono<MerchantBalanceDto> getMerchantBalance(String merchantId, String bearer) {
        return client.get()
            .uri("/api/refunds/merchant/{merchantId}/balance", merchantId)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToMono(MerchantBalanceDto.class)
            .timeout(Duration.ofSeconds(5))
            .doOnError(e -> log.error("[MQC][ERR] getMerchantBalance merchantId={} msg={}", merchantId, e.getMessage()))
            .onErrorResume(e -> Mono.empty());
    }

    /**
     * Crear un retiro de dinero (withdrawal) desde un payment intent
     */
    public Mono<WithdrawalDto> createWithdrawal(String paymentIntentId, CreateWithdrawalRequest request, String bearer) {
        return client.post()
            .uri("/api/payment-intents/{id}/refunds", paymentIntentId)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(WithdrawalDto.class)
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess(wd -> log.info("[MQC][SUCCESS] createWithdrawal id={} amount={}",
                wd != null ? wd.getId() : "null", wd != null ? wd.getAmount() : "null"))
            .doOnError(e -> log.error("[MQC][ERR] createWithdrawal paymentIntentId={} amount={} msg={}",
                paymentIntentId, request.getAmount(), e.getMessage()));
    }

    /**
     * Obtener todos los retiros de un merchant
     */
    public Flux<WithdrawalDto> getWithdrawals(String merchantId, String bearer, String status) {
        return client.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/api/refunds").queryParam("merchantId", merchantId);
                if (status != null && !status.equals("all")) {
                    builder = builder.queryParam("status", status);
                }
                return builder.build();
            })
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToFlux(WithdrawalDto.class)
            .timeout(Duration.ofSeconds(10))
            .doOnError(e -> log.error("[MQC][ERR] getWithdrawals merchantId={} status={} msg={}",
                merchantId, status, e.getMessage()))
            .onErrorResume(e -> Flux.empty());
    }

    /**
     * Obtener los retiros de un payment intent específico
     */
    public Flux<WithdrawalDto> getWithdrawalsByPaymentIntent(String paymentIntentId, String bearer) {
        return client.get()
            .uri("/api/payment-intents/{id}/refunds", paymentIntentId)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToFlux(WithdrawalDto.class)
            .timeout(Duration.ofSeconds(5))
            .doOnError(e -> log.error("[MQC][ERR] getWithdrawalsByPaymentIntent paymentIntentId={} msg={}",
                paymentIntentId, e.getMessage()))
            .onErrorResume(e -> Flux.empty());
    }

    /**
     * Obtener un retiro específico por ID
     */
    public Mono<WithdrawalDto> getWithdrawal(String withdrawalId, String bearer) {
        return client.get()
            .uri("/api/refunds/{id}", withdrawalId)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToMono(WithdrawalDto.class)
            .timeout(Duration.ofSeconds(5))
            .doOnError(e -> log.error("[MQC][ERR] getWithdrawal withdrawalId={} msg={}", withdrawalId, e.getMessage()))
            .onErrorResume(e -> Mono.empty());
    }

    /**
     * Cancelar un retiro pendiente
     */
    public Mono<Void> cancelWithdrawal(String withdrawalId, String bearer) {
        return client.post()
            .uri("/api/refunds/{id}/cancel", withdrawalId)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .retrieve()
            .bodyToMono(Void.class)
            .timeout(Duration.ofSeconds(5))
            .doOnSuccess(v -> log.info("[MQC][SUCCESS] cancelWithdrawal withdrawalId={}", withdrawalId))
            .doOnError(e -> log.error("[MQC][ERR] cancelWithdrawal withdrawalId={} msg={}", withdrawalId, e.getMessage()));
    }
}
