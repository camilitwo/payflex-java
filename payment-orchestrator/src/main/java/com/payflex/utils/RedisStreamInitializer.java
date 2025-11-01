package com.payflex.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RedisStreamInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RedisStreamInitializer.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisStreamInitializer(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        final String stream = RedisStreams.PAYMENT_EVENTS;
        final String group = "payment-consumers";
        byte[] streamKey = stream.getBytes(StandardCharsets.UTF_8);

        try (RedisConnection connection = connectionFactory.getConnection()) {
            try {
                // intentar crear el grupo; mkstream=true creará el stream si no existe
                // Usar ReadOffset.from("0-0") para indicar desde el inicio (ID '0-0') — formato válido para Redis Streams
                connection.streamCommands().xGroupCreate(streamKey, group, ReadOffset.from("0-0"), true);
                log.info("Created consumer group '{}' for stream '{}'", group, stream);
            } catch (Exception e) {
                // si ya existe o fallo, log y seguir
                log.info("Could not create group '{}': {}", group, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error while initializing Redis stream group: {}", e.getMessage(), e);
        }
    }
}
