package com.payflex.repository;


import com.payflex.model.MerchantBalance;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MerchantBalanceRepository extends ReactiveCrudRepository<MerchantBalance, Long> {

    Mono<MerchantBalance> findByMerchantId(String merchantId);
}

