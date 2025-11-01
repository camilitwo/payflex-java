package com.payflex.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
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
            boolean groupExists = false;
            try {
                StreamInfo.XInfoGroups groups = connection.streamCommands().xInfoGroups(streamKey);
                if (groups != null) {
                    for (StreamInfo.XInfoGroup g : groups) {
                        if (group.equals(g.groupName())) {
                            groupExists = true;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // xInfoGroups lanzará si el stream no existe; ignoramos y seguiremos a crear grupo
                log.info("Stream might not exist yet: {}. Will attempt to create group.", stream);
            }

            if (!groupExists) {
                try {
                    // Intentar crear el grupo; si el stream no existe, se puede crear con mkstream=true
                    connection.streamCommands().xGroupCreate(streamKey, group, ReadOffset.lastConsumed(), true);
                    log.info("Created consumer group '{}' for stream '{}'", group, stream);
                } catch (Exception e) {
                    log.error("Failed to create consumer group '{}': {}", group, e.getMessage(), e);
                }
            } else {
                log.info("Consumer group '{}' already exists for stream '{}'", group, stream);
            }

        } catch (Exception e) {
            log.error("Error while initializing Redis stream groups: {}", e.getMessage(), e);
        }
    }
}
