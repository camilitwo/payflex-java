package com.payflex.merchant.repository;

import com.payflex.merchant.model.Merchant;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MerchantRepository extends ReactiveCrudRepository<Merchant, String> {


    Mono<Merchant> findByEmail(String email);

    Flux<Merchant> findByStatus(String status);
}
