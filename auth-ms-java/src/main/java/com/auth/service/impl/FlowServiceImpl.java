package com.auth.service.impl;

import com.auth.client.MerchantQueryClient;
import com.auth.dto.CreatePaymentIntentRequest;
import com.auth.dto.PaymentDTO;
import com.auth.dto.PaymentStatus;
import com.auth.service.FlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowServiceImpl implements FlowService {

    private final WebClient flowWebClient;
    private final MerchantQueryClient merchantQueryClient;
    private final RedisTemplate<String, PaymentDTO> paymentRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private final String apiKey;
    private final String secretKey;
    private final String publicUrl;

    // Prefijos para las claves de Redis
    private static final String PAYMENT_KEY_PREFIX = "payment:";
    private static final String TOKEN_KEY_PREFIX = "token:";
    private static final long PAYMENT_EXPIRATION_HOURS = 24; // Los pagos expiran en 24 horas

    public FlowServiceImpl(WebClient flowWebClient,
                       MerchantQueryClient merchantQueryClient,
                       @Qualifier("paymentRedisTemplate") RedisTemplate<String, PaymentDTO> paymentRedisTemplate,
                       StringRedisTemplate stringRedisTemplate,
                       @Value("${flow.api-key}") String apiKey,
                       @Value("${flow.secret-key}") String secretKey,
                       @Value("${flow.public-url}") String publicUrl) {
        this.flowWebClient = flowWebClient;
        this.merchantQueryClient = merchantQueryClient;
        this.paymentRedisTemplate = paymentRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.publicUrl = publicUrl;
    }

    @Override
    public Mono<String> createPayment(String merchantId, String email, Long amount, String subject) {
        String commerceOrder = UUID.randomUUID().toString();

        log.info("[FlowService] Iniciando pago - merchantId={}, email={}, amount={}, subject={}",
                merchantId, email, amount, subject);

        // Crear PaymentIntent en el sistema con merchantId del JWT
        CreatePaymentIntentRequest piRequest = CreatePaymentIntentRequest.builder()
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(amount))
                .currency("CLP")
                .description(subject)
                .statementDescriptor(subject)
                .build();

        // Crear el payment intent de forma reactiva
        return merchantQueryClient
                .createPaymentIntent(piRequest, "")
                .flatMap(paymentIntent -> {
                    if (paymentIntent == null || paymentIntent.getId() == null) {
                        return Mono.error(new IllegalStateException("No se pudo crear el PaymentIntent"));
                    }

                    log.info("[FlowService] PaymentIntent creado - id={}, merchantId={}",
                            paymentIntent.getId(), merchantId);

                    // Crear entidad de pago local
                    PaymentDTO entity = new PaymentDTO();
                    entity.setCommerceOrder(commerceOrder);
                    entity.setEmail(email);
                    entity.setAmount(amount);
                    entity.setSubject(subject);
                    entity.setStatus(PaymentStatus.CREATED);

                    // Guardar en Redis de forma reactiva
                    String paymentKey = PAYMENT_KEY_PREFIX + commerceOrder;
                    return Mono.fromRunnable(() ->
                        paymentRedisTemplate.opsForValue().set(paymentKey, entity, PAYMENT_EXPIRATION_HOURS, TimeUnit.HOURS)
                    ).thenReturn(entity);
                })
                .flatMap(entity -> {
                    // Crear el pago en Flow
                    Map<String, String> params = Map.ofEntries(
                            Map.entry("apiKey", apiKey),
                            Map.entry("commerceOrder", commerceOrder),
                            Map.entry("urlConfirmation", publicUrl + "/api/flow/confirmation"),
                            Map.entry("urlReturn", publicUrl + "/flow/return"),
                            Map.entry("email", email),
                            Map.entry("subject", subject),
                            Map.entry("amount", amount.toString()),
                            Map.entry("currency", "CLP")
                    );

                    String signature;
                    try {
                        signature = signParams(params, secretKey);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Error generando firma", e));
                    }

                    log.debug("[FlowService] Params enviados a Flow: {}", params);
                    log.debug("[FlowService] Firma s={}", signature);

                    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                    params.forEach(formData::add);
                    formData.add("s", signature);



                    return flowWebClient.post()
                            .uri("/payment/create")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(BodyInserters.fromFormData(formData))
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, (ClientResponse resp) ->
                                    resp.bodyToMono(String.class).flatMap(body -> {
                                        log.error("[FlowService] Error HTTP {} en payment/create. Body: {}",
                                                resp.statusCode(), body);
                                        return Mono.error(new RuntimeException("Error Flow: " + resp.statusCode()));
                                    })
                            )
                            .bodyToMono(Map.class)
                            .flatMap(response -> {
                                if (response == null || !response.containsKey("url") || !response.containsKey("token")) {
                                    return Mono.error(new IllegalStateException("Respuesta inválida de Flow"));
                                }

                                String url = (String) response.get("url");
                                String token = (String) response.get("token");

                                // Actualizar entidad con token de Flow
                                entity.setStatus(PaymentStatus.PENDING);
                                String paymentKey = PAYMENT_KEY_PREFIX + commerceOrder;
                                String tokenKey = TOKEN_KEY_PREFIX + token;

                                return Mono.fromRunnable(() -> {
                                    paymentRedisTemplate.opsForValue().set(paymentKey, entity, PAYMENT_EXPIRATION_HOURS, TimeUnit.HOURS);
                                    stringRedisTemplate.opsForValue().set(tokenKey, commerceOrder, PAYMENT_EXPIRATION_HOURS, TimeUnit.HOURS);
                                }).thenReturn(url + "?token=" + token);
                            });
                })
                .doOnSuccess(flowUrl ->
                    log.info("[FlowService] Pago creado exitosamente - commerceOrder={}, merchantId={}",
                            commerceOrder, merchantId)
                )
                .doOnError(e ->
                    log.error("[FlowService] Error creando pago en Flow", e)
                );
    }

    @Override
    public Mono<PaymentDTO> handleConfirmation(String token) {
        log.info("[FlowService] Procesando confirmación - token={}", token);

        String tokenKey = TOKEN_KEY_PREFIX + token;

        return Mono.fromCallable(() -> stringRedisTemplate.opsForValue().get(tokenKey))
                .flatMap(commerceOrder -> {
                    if (commerceOrder == null) {
                        return Mono.error(new IllegalArgumentException("Token Flow no encontrado"));
                    }

                    String paymentKey = PAYMENT_KEY_PREFIX + commerceOrder;

                    return Mono.fromCallable(() -> paymentRedisTemplate.opsForValue().get(paymentKey))
                            .flatMap(entity -> {
                                if (entity == null) {
                                    return Mono.error(new IllegalArgumentException("Orden no encontrada"));
                                }

                                Map<String, String> params = Map.of(
                                        "apiKey", apiKey,
                                        "token", token
                                );

                                String signature;
                                try {
                                    signature = signParams(params, secretKey);
                                } catch (Exception e) {
                                    return Mono.error(new RuntimeException("Error generando firma", e));
                                }

                                String query = params.entrySet().stream()
                                        .sorted(Map.Entry.comparingByKey())
                                        .map(e -> e.getKey() + "=" + e.getValue())
                                        .collect(Collectors.joining("&")) + "&s=" + signature;

                                return flowWebClient.get()
                                        .uri("/payment/getStatus?" + query)
                                        .retrieve()
                                        .bodyToMono(Map.class)
                                        .flatMap(response -> {
                                            if (response == null) {
                                                return Mono.error(new IllegalStateException("No se pudo obtener el estado del pago desde Flow"));
                                            }

                                            Integer flowStatus = (Integer) response.get("status");

                                            PaymentStatus newStatus = switch (flowStatus) {
                                                case 2 -> PaymentStatus.PAID;
                                                case 3 -> PaymentStatus.REJECTED;
                                                case 4 -> PaymentStatus.CANCELED;
                                                default -> PaymentStatus.PENDING;
                                            };

                                            entity.setStatus(newStatus);

                                            return Mono.fromRunnable(() ->
                                                paymentRedisTemplate.opsForValue().set(paymentKey, entity, PAYMENT_EXPIRATION_HOURS, TimeUnit.HOURS)
                                            ).thenReturn(entity);
                                        })
                                        .doOnSuccess(updatedEntity ->
                                            log.info("[FlowService] Confirmación procesada - commerceOrder={}, newStatus={}",
                                                    commerceOrder, updatedEntity.getStatus())
                                        );
                            });
                });
    }

    @Override
    public Mono<PaymentDTO> getPayment(String commerceOrder) {
        String paymentKey = PAYMENT_KEY_PREFIX + commerceOrder;

        return Mono.fromCallable(() -> paymentRedisTemplate.opsForValue().get(paymentKey))
                .flatMap(payment -> {
                    if (payment == null) {
                        return Mono.error(new IllegalArgumentException("Orden no encontrada"));
                    }
                    return Mono.just(payment);
                });
    }

    private String signParams(Map<String, String> params, String secretKey) throws Exception {
        String data = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + e.getValue())
                .collect(Collectors.joining());

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

