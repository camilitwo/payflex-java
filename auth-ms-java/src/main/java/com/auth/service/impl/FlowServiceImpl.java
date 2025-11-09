package com.auth.service.impl;

import com.auth.client.MerchantQueryClient;
import com.auth.dto.CreatePaymentIntentRequest;
import com.auth.dto.PaymentDTO;
import com.auth.dto.PaymentIntentDto;
import com.auth.dto.PaymentStatus;
import com.auth.service.FlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

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
    public String createPayment(String merchantId, String email, Long amount, String subject) {
        try {
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

            // Crear el payment intent y esperar el resultado
            PaymentIntentDto paymentIntent = merchantQueryClient
                    .createPaymentIntent(piRequest, "")
                    .block();

            if (paymentIntent == null || paymentIntent.getId() == null) {
                throw new IllegalStateException("No se pudo crear el PaymentIntent");
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

            // Guardar en Redis con expiración
            String paymentKey = PAYMENT_KEY_PREFIX + commerceOrder;
            paymentRedisTemplate.opsForValue().set(paymentKey, entity, PAYMENT_EXPIRATION_HOURS, TimeUnit.HOURS);

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

            String signature = signParams(params, secretKey);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            params.forEach(formData::add);
            formData.add("s", signature);

            var response = flowWebClient.post()
                    .uri("/payment/create")
                    .bodyValue(formData)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("url") || !response.containsKey("token")) {
                throw new IllegalStateException("Respuesta inválida de Flow");
            }

            String url = (String) response.get("url");
            String token = (String) response.get("token");

            // Actualizar entidad con token de Flow
            entity.setStatus(PaymentStatus.PENDING);
            paymentRedisTemplate.opsForValue().set(paymentKey, entity, PAYMENT_EXPIRATION_HOURS, TimeUnit.HOURS);

            // Mapear token a commerceOrder en Redis
            String tokenKey = TOKEN_KEY_PREFIX + token;
            stringRedisTemplate.opsForValue().set(tokenKey, commerceOrder, PAYMENT_EXPIRATION_HOURS, TimeUnit.HOURS);

            log.info("[FlowService] Pago creado exitosamente - commerceOrder={}, flowToken={}, merchantId={}",
                    commerceOrder, token, merchantId);

            return url + "?token=" + token;
        } catch (Exception e) {
            log.error("[FlowService] Error creando pago en Flow", e);
            throw new RuntimeException("Error creando pago en Flow", e);
        }
    }

    @Override
    public PaymentDTO handleConfirmation(String token) throws Exception {
        log.info("[FlowService] Procesando confirmación - token={}", token);

        // Buscar orden por token en Redis
        String tokenKey = TOKEN_KEY_PREFIX + token;
        String commerceOrder = stringRedisTemplate.opsForValue().get(tokenKey);

        if (commerceOrder == null) {
            throw new IllegalArgumentException("Token Flow no encontrado");
        }

        String paymentKey = PAYMENT_KEY_PREFIX + commerceOrder;
        PaymentDTO entity = paymentRedisTemplate.opsForValue().get(paymentKey);

        if (entity == null) {
            throw new IllegalArgumentException("Orden no encontrada");
        }

        Map<String, String> params = Map.of(
                "apiKey", apiKey,
                "token", token
        );
        String signature = signParams(params, secretKey);

        String query = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + "&s=" + signature;

        Map<String, Object> response = flowWebClient.get()
                .uri("/payment/getStatus?" + query)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new IllegalStateException("No se pudo obtener el estado del pago desde Flow");
        }

        // Flow responde con status numérico:
        // 1: pendiente, 2: pagada, 3: rechazada, 4: anulada
        Integer flowStatus = (Integer) response.get("status");

        PaymentStatus newStatus = switch (flowStatus) {
            case 2 -> PaymentStatus.PAID;
            case 3 -> PaymentStatus.REJECTED;
            case 4 -> PaymentStatus.CANCELED;
            default -> PaymentStatus.PENDING;
        };

        entity.setStatus(newStatus);
        paymentRedisTemplate.opsForValue().set(paymentKey, entity, PAYMENT_EXPIRATION_HOURS, TimeUnit.HOURS);

        log.info("[FlowService] Confirmación procesada - commerceOrder={}, newStatus={}", commerceOrder, newStatus);

        return entity;
    }

    @Override
    public PaymentDTO getPayment(String commerceOrder) {
        String paymentKey = PAYMENT_KEY_PREFIX + commerceOrder;
        PaymentDTO payment = paymentRedisTemplate.opsForValue().get(paymentKey);

        if (payment == null) {
            throw new IllegalArgumentException("Orden no encontrada");
        }
        return payment;
    }

    private String signParams(Map<String, String> params, String secretKey) throws Exception {
        // Ordena keys alfabéticamente y concatena key+value
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
