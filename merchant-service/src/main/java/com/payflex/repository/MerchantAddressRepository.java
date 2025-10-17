package com.payflex.merchant.repository;

import com.payflex.merchant.model.MerchantAddress;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MerchantAddressRepository extends ReactiveCrudRepository<MerchantAddress, Long> {

    Flux<MerchantAddress> findByMerchantId(String merchantId);

    Flux<MerchantAddress> findByMerchantIdAndAddressType(String merchantId, String addressType);

    Mono<MerchantAddress> findByMerchantIdAndIsPrimary(String merchantId, Boolean isPrimary);
}
