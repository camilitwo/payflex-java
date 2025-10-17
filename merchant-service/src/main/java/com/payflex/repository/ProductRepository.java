package com.payflex.merchant.repository;

import com.payflex.merchant.model.Product;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProductRepository extends ReactiveCrudRepository<Product, String> {

    Flux<Product> findByMerchantId(String merchantId);

    Flux<Product> findByMerchantIdAndStatus(String merchantId, String status);

    Mono<Product> findByMerchantIdAndSku(String merchantId, String sku);

    Flux<Product> findByMerchantIdOrderByCreatedAtDesc(String merchantId);
}
