package com.payflex.merchant.repository;

import com.payflex.merchant.model.MerchantBalance;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MerchantBalanceRepository extends ReactiveCrudRepository<MerchantBalance, Long> {

    Mono<MerchantBalance> findByMerchantId(String merchantId);
}

