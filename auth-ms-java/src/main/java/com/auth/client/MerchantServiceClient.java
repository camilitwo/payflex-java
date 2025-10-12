package com.auth.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Component
public class MerchantServiceClient {

  private static final Logger log = LoggerFactory.getLogger(MerchantServiceClient.class);
  private final WebClient webClient;

  public MerchantServiceClient(WebClient.Builder webClientBuilder,
      @Value("${merchant.service.url:http://merchant-service}") String merchantServiceUrl,
      @Value("${merchant.service.timeout.connect:5000}") int connectTimeout,
      @Value("${merchant.service.timeout.read:10000}") int readTimeout) {

    this.webClient = webClientBuilder
        .baseUrl(merchantServiceUrl)
        .build();

    log.info("MerchantServiceClient initialized - URL={} ConnectTimeout={}ms ReadTimeout={}ms",
        merchantServiceUrl, connectTimeout, readTimeout);
  }

  public Mono<Map<String, Object>> createMerchant(Map<String, Object> request) {
    log.info("🔵 [MerchantServiceClient] Llamando POST /merchants merchantId={} email={}",
        request.get("merchantId"), request.get("email"));
    log.debug("🔵 [MerchantServiceClient] Payload completo: {}", request);

    return webClient.post()
        .uri("/merchants")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchangeToMono(response -> {
          if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnNext(body -> log.info("✅ [MerchantServiceClient] Merchant creado exitosamente merchantId={}", body.get("merchantId")));
          }
          return response.bodyToMono(String.class)
              .defaultIfEmpty("<empty-body>")
              .flatMap(body -> {
                log.error("🔴 [MerchantServiceClient] Error status={} body={}", response.statusCode(), body);
                return Mono.error(new IllegalStateException("merchant-service status=" + response.statusCode() + " body=" + body));
              });
        })
        .timeout(Duration.ofSeconds(10))
        .doOnError(err -> log.error("🔴 [MerchantServiceClient] Fallo en invocación: {}", err.getMessage(), err))
        .onErrorResume(err -> {
          log.warn("⚠️  [MerchantServiceClient] Retornando empty debido a error (no bloquea auth)");
          return Mono.empty();
        });
  }
}
