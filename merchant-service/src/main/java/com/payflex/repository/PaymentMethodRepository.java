package com.payflex.repository;


import com.payflex.model.PaymentMethod;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PaymentMethodRepository extends ReactiveCrudRepository<PaymentMethod, String> {

    Flux<PaymentMethod> findByCustomerId(String customerId);

    Flux<PaymentMethod> findByMerchantId(String merchantId);

    Mono<PaymentMethod> findByCustomerIdAndIsDefault(String customerId, Boolean isDefault);

    Flux<PaymentMethod> findByMerchantIdAndType(String merchantId, String type);
}
