package com.payflex.repository;

import com.payflex.model.Customer;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CustomerRepository extends ReactiveCrudRepository<Customer, String> {

    Flux<Customer> findByMerchantId(String merchantId);

    Mono<Customer> findByMerchantIdAndEmail(String merchantId, String email);

    Flux<Customer> findByMerchantIdOrderByCreatedAtDesc(String merchantId);
}
