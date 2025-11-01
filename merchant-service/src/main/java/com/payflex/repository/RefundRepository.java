package com.payflex.repository;

import com.payflex.model.Refund;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface RefundRepository extends ReactiveCrudRepository<Refund, String> {

    Flux<Refund> findByPaymentIntentId(String paymentIntentId);

    Flux<Refund> findByChargeId(String chargeId);

    Flux<Refund> findByMerchantId(String merchantId);

    Flux<Refund> findByMerchantIdAndStatus(String merchantId, String status);

    @Query("SELECT SUM(amount) FROM refunds WHERE charge_id = :chargeId AND status = 'succeeded'")
    Mono<Double> sumRefundedAmountByChargeId(String chargeId);

    @Query("SELECT SUM(amount) FROM refunds WHERE payment_intent_id = :paymentIntentId AND status = 'succeeded'")
    Mono<Double> sumRefundedAmountByPaymentIntentId(String paymentIntentId);

    // Suma de refunds por merchant, estado y rango de fechas (para egresos del dashboard)
    @Query("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE merchant_id = :merchantId AND status = :status AND created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> sumAmountByMerchantIdAndStatusAndCreatedAtBetween(
            String merchantId,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}
