package com.payflex.repository;

import com.payflex.model.MerchantBalance;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MerchantBalanceRepository extends ReactiveCrudRepository<MerchantBalance, Integer> {

    Mono<MerchantBalance> findByMerchantId(String merchantId);

    @Query("UPDATE merchant_balances SET available_balance = available_balance + :amount, updated_at = CURRENT_TIMESTAMP WHERE merchant_id = :merchantId")
    Mono<Void> incrementAvailableBalance(String merchantId, Double amount);

    @Query("UPDATE merchant_balances SET available_balance = available_balance - :amount, updated_at = CURRENT_TIMESTAMP WHERE merchant_id = :merchantId AND available_balance >= :amount")
    Mono<Integer> decrementAvailableBalance(String merchantId, Double amount);
}


