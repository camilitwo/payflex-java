package com.payflex.merchant.repository;

import com.payflex.merchant.model.PaymentIntent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PaymentIntentRepository extends ReactiveCrudRepository<PaymentIntent, String> {

    Flux<PaymentIntent> findByMerchantId(String merchantId);

    Flux<PaymentIntent> findByMerchantIdAndStatus(String merchantId, String status);

    Flux<PaymentIntent> findByCustomerId(String customerId);

    Flux<PaymentIntent> findByMerchantIdOrderByCreatedAtDesc(String merchantId);
}
