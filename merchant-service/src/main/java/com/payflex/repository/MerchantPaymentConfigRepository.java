package com.payflex.merchant.repository;

import com.payflex.merchant.model.MerchantPaymentConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MerchantPaymentConfigRepository extends ReactiveCrudRepository<MerchantPaymentConfig, Long> {

    Mono<MerchantPaymentConfig> findByMerchantId(String merchantId);
}
