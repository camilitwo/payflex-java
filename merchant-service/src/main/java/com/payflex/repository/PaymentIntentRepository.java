package com.payflex.repository;


import com.payflex.model.PaymentIntent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface PaymentIntentRepository extends ReactiveCrudRepository<PaymentIntent, String> {

    Flux<PaymentIntent> findByMerchantId(String merchantId);

    Flux<PaymentIntent> findByMerchantIdAndStatus(String merchantId, String status);

    Flux<PaymentIntent> findByCustomerId(String customerId);

    Flux<PaymentIntent> findByMerchantIdOrderByCreatedAtDesc(String merchantId);

    // Nuevas consultas para estadísticas
    Flux<PaymentIntent> findByMerchantIdAndStatusAndCreatedAtBetween(
        String merchantId,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    @Query("SELECT COUNT(*) FROM payment_intents WHERE merchant_id = :merchantId AND status = :status AND created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> countByMerchantIdAndStatusAndCreatedAtBetween(
        String merchantId,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payment_intents WHERE merchant_id = :merchantId AND status = :status AND created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> sumAmountByMerchantIdAndStatusAndCreatedAtBetween(
        String merchantId,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
}
