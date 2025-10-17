package com.payflex.merchant.repository;

import com.payflex.merchant.model.MerchantUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MerchantUserRepository extends ReactiveCrudRepository<MerchantUser, String> {

    Mono<MerchantUser> findByUserId(String userId);

    Flux<MerchantUser> findByMerchantId(String merchantId);

    Mono<MerchantUser> findByUserIdAndMerchantId(String userId, String merchantId);

    Flux<MerchantUser> findByMerchantIdAndStatus(String merchantId, String status);

    Flux<MerchantUser> findByRole(String role);

    @Query("""
        INSERT INTO merchant_users (user_id, merchant_id, email, password_hash, name, phone, role, status, email_verified, last_login_at, created_at, updated_at)
        VALUES (:userId, :merchantId, :email, :passwordHash, :name, :phone, :role, :status, :emailVerified, :lastLoginAt, :createdAt, :updatedAt)
        ON CONFLICT (user_id) DO UPDATE SET
            merchant_id = EXCLUDED.merchant_id,
            email = EXCLUDED.email,
            password_hash = EXCLUDED.password_hash,
            name = EXCLUDED.name,
            phone = EXCLUDED.phone,
            role = EXCLUDED.role,
            status = EXCLUDED.status,
            email_verified = EXCLUDED.email_verified,
            last_login_at = EXCLUDED.last_login_at,
            updated_at = EXCLUDED.updated_at
        """)
    Mono<Void> upsertMerchantUser(String userId, String merchantId, String email, String passwordHash,
                                  String name, String phone, String role, String status,
                                  Boolean emailVerified, java.time.LocalDateTime lastLoginAt,
                                  java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt);
}
