package com.payflex.event.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflex.client.MerchantServiceClient;
import com.payflex.dto.CreatePaymentIntentRequest;
import com.payflex.dto.PaymentIntentResponse;
import com.payflex.utils.RedisStreams;
import io.lettuce.core.RedisCommandExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final MerchantServiceClient merchantServiceClient;
    private final ObjectMapper objectMapper;
    private final RedisConnectionFactory connectionFactory;

    @Value("${redis.stream.consumer.group:payment-consumers}")
    private String consumerGroup;

    @Value("${redis.stream.consumer.name:}")
    private String consumerName;

    // Nombre resolvido una sola vez
    private String effectiveConsumerName;

    public PaymentEventConsumer(RedisTemplate<String, Object> redisTemplate,
                                MerchantServiceClient merchantServiceClient,
                                ObjectMapper objectMapper,
                                RedisConnectionFactory connectionFactory) {
        this.redisTemplate = redisTemplate;
        this.merchantServiceClient = merchantServiceClient;
        this.objectMapper = objectMapper;
        this.connectionFactory = connectionFactory;
    }

    @PostConstruct
    private void initConsumerName() {
        this.effectiveConsumerName = resolveConsumerName();
        log.info("Using consumer group='{}' consumerName='{}'", consumerGroup, effectiveConsumerName);
    }

    private String resolveConsumerName() {
        if (consumerName != null && !consumerName.isBlank()) return consumerName;
        try {
            String host = InetAddress.getLocalHost().getHostName();
            return host + "-" + UUID.randomUUID();
        } catch (Exception e) {
            return "consumer-" + UUID.randomUUID();
        }
    }

    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelayString = "${redis.stream.consumer.poll-ms:1000}")
    public void consume() {
        Consumer consumer = Consumer.from(consumerGroup, effectiveConsumerName);

        // Intentar leer; si falla por NOGROUP intentar crear el grupo y reintentar una vez
        List<MapRecord<String, Object, Object>> messages;
        boolean retried = false;
        while (true) {
            try {
                messages = redisTemplate.opsForStream().read(
                        consumer,
                        StreamReadOptions.empty().count(10).block(Duration.ofMillis(1000)),
                        StreamOffset.create(RedisStreams.PAYMENT_EVENTS, ReadOffset.lastConsumed())
                );
                break; // éxito
            } catch (Exception e) {
                Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                // detectar NOGROUP en excepciones de Lettuce
                if (!retried && (cause instanceof RedisCommandExecutionException || (cause.getMessage() != null && cause.getMessage().contains("NOGROUP")))) {
                    log.warn("Read failed due to missing group. Will attempt to create group '{}' for stream '{}', then retry. Error: {}", consumerGroup, RedisStreams.PAYMENT_EVENTS, cause.getMessage());
                    try {
                        byte[] streamKey = RedisStreams.PAYMENT_EVENTS.getBytes(StandardCharsets.UTF_8);
                        try (var conn = connectionFactory.getConnection()) {
                            // Usar ReadOffset.from("0-0") para indicar ID '0-0' al crear el grupo (evita error de ID inválido)
                            conn.streamCommands().xGroupCreate(streamKey, consumerGroup, ReadOffset.from("0-0"), true);
                        }
                        retried = true;
                        continue; // reintentar la lectura
                    } catch (Exception ex) {
                        log.error("Failed to create consumer group on retry: {}", ex.getMessage(), ex);
                        // no más reintentos
                        return;
                    }
                }

                // otro tipo de error -> log y salir
                log.error("Error reading from stream: {}", e.getMessage(), e);
                return;
            }
        }

        if (messages == null || messages.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> message : messages) {
            try {
                handleMessage(message);
                // ACK only after successful processing
                redisTemplate.opsForStream().acknowledge(
                        RedisStreams.PAYMENT_EVENTS,
                        consumerGroup,
                        message.getId()
                );
            } catch (Exception e) {
                // aquí podrías mandar a DLQ o loguear
                log.error("Error processing message {}: {}", message.getId(), e.getMessage(), e);
            }
        }
    }

    private void handleMessage(MapRecord<String, Object, Object> message) {
        Map<Object, Object> value = message.getValue();
        String eventType = (String) value.get("eventType");
        log.info("Event received: {} -> {}", eventType, value);

        if ("PAYMENT_APPROVED".equals(eventType)) {
            // deserializar payload a CreatePaymentIntentRequest
            Object payloadObj = value.get("payload");
            if (payloadObj == null) {
                log.warn("PAYMENT_APPROVED event without payload: {}", message.getId());
                return;
            }

            String payloadJson;
            try {
                if (payloadObj instanceof String) {
                    payloadJson = (String) payloadObj;
                } else {
                    // si por alguna razón el payload viene como mapa u objeto, serializamos a JSON
                    payloadJson = objectMapper.writeValueAsString(payloadObj);
                }

                CreatePaymentIntentRequest request = objectMapper.readValue(payloadJson, CreatePaymentIntentRequest.class);

                // Llamar al merchant service para crear/inserir en la base de datos via su endpoint
                PaymentIntentResponse response = merchantServiceClient.createPaymentIntent(request);

                // loguear la respuesta y cualquier acción adicional
                log.info("Created payment intent via merchant service. id={}, status={}", response.getId(), response.getStatus());

                // Aquí podrías guardar una auditoría local o desencadenar otros procesos

            } catch (JsonProcessingException jpe) {
                log.error("Failed to (de)serialize payload for message {}: {}", message.getId(), jpe.getMessage(), jpe);
                throw new RuntimeException("Payload deserialization error", jpe);
            } catch (Exception e) {
                // errores de red/feign/DB en merchant-service
                log.error("Error while creating payment intent for message {}: {}", message.getId(), e.getMessage(), e);
                throw new RuntimeException("Error calling merchant service", e);
            }
        }
    }
}
