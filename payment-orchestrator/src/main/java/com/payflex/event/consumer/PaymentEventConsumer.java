package com.payflex.event.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflex.client.MerchantServiceClient;
import com.payflex.dto.CreatePaymentIntentRequest;
import com.payflex.dto.PaymentIntentResponse;
import com.payflex.utils.RedisStreams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
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

    @Value("${redis.stream.consumer.group:payment-consumers}")
    private String consumerGroup;

    @Value("${redis.stream.consumer.name:}")
    private String consumerName;

    // Nombre resolvido una sola vez
    private String effectiveConsumerName;

    public PaymentEventConsumer(RedisTemplate<String, Object> redisTemplate,
                                MerchantServiceClient merchantServiceClient,
                                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.merchantServiceClient = merchantServiceClient;
        this.objectMapper = objectMapper;
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

    @Scheduled(fixedDelayString = "${redis.stream.consumer.poll-ms:1000}")
    public void consume() {
        Consumer consumer = Consumer.from(consumerGroup, effectiveConsumerName);

        // Leer 10 eventos pendientes del grupo
        List<MapRecord<String, Object, Object>> messages =
                redisTemplate.opsForStream().read(
                        consumer,
                        StreamReadOptions.empty().count(10).block(Duration.ofMillis(1000)),
                        StreamOffset.create(RedisStreams.PAYMENT_EVENTS, ReadOffset.lastConsumed())
                );

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
