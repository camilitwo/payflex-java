package com.payflex.event.producer.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflex.dto.CreatePaymentIntentRequest;
import com.payflex.utils.RedisStreams;
import com.payflex.event.producer.PaymentEventProducer;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentEventProducerImpl implements PaymentEventProducer {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventProducerImpl(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentApproved(String paymentId, String merchantId, Integer amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "PAYMENT_APPROVED");
        body.put("paymentId", paymentId);
        body.put("merchantId", merchantId);
        body.put("amount", amount);
        body.put("occurredAt", System.currentTimeMillis());

        // XADD payflex:payment-events * field value ...
        RecordId id = redisTemplate.opsForStream()
                .add(RedisStreams.PAYMENT_EVENTS, body);

        // opcional log
        System.out.println("Event sent to stream with id = " + id);
    }

    @Override
    public void publishPaymentApproved(CreatePaymentIntentRequest request) {
        // Construir el mapa con el payload completo como JSON + metadatos
        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "PAYMENT_APPROVED");
        body.put("occurredAt", System.currentTimeMillis());

        // intentar extraer un paymentId del request si existe, para indexación rápida
        try {
            // si CreatePaymentIntentRequest tiene getPaymentId() lo añadimos; si no, se ignora
            String paymentId = null;
            try {
                Object maybeId = request.getClass().getMethod("getPaymentId").invoke(request);
                if (maybeId != null) {
                    paymentId = String.valueOf(maybeId);
                }
            } catch (NoSuchMethodException ignored) {
                // no existe el método, no hacemos nada
            }

            if (paymentId != null) {
                body.put("paymentId", paymentId);
            }

            // Serializamos todo el request como JSON en el campo 'payload'
            String payloadJson = objectMapper.writeValueAsString(request);
            body.put("payload", payloadJson);

        } catch (JsonProcessingException e) {
            // Si falla la serialización, guardamos una representación toString() y un flag de error
            body.put("payload", String.valueOf(request));
            body.put("payloadSerializationError", true);
        } catch (Exception e) {
            // Errores reflexivos u otros
            body.put("payload", String.valueOf(request));
            body.put("payloadSerializationError", true);
        }

        RecordId id = redisTemplate.opsForStream()
                .add(RedisStreams.PAYMENT_EVENTS, body);

        System.out.println("Event sent to stream with id = " + id + " payloadKeyPresent=" + body.containsKey("payload"));
    }
}
